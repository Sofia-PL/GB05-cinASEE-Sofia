package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.clients.dto.GatewayRefundRequest;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.events.OutboxService;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Caso de uso: anulaciones y devoluciones (DELETE /reservations/{reservationId}).
 *
 * <ul>
 *   <li><b>PENDING_PAYMENT</b> → CANCELLED: se liberan las butacas al instante
 *       (por ejemplo, el usuario cierra el proceso de compra), sin esperar a los 10 min.</li>
 *   <li><b>CONFIRMED</b> → REFUNDED: solo si la sesión no ha empezado y ninguna entrada
 *       se ha usado. Se ordena la devolución a la pasarela, se liberan las butacas
 *       para que puedan revenderse y se publica PaymentRefunded.</li>
 *   <li>Cualquier otro estado → 409 (la reserva ya está cerrada).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final ReservationRepository reservationRepository;
    private final SeatLockService seatLockService;
    private final PaymentGateway paymentGateway;
    private final OutboxService outboxService;
    private final Clock clock;

    @Transactional
    public ReservationResponse cancelReservation(Long reservationId, CallerIdentity caller) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva " + reservationId + " no existe"));
        ReservationAccessPolicy.checkCanAccess(reservation, caller);
        Instant now = clock.instant();

        switch (reservation.getStatus()) {
            case PENDING_PAYMENT -> {
                ReservationStatus finalStatus = reservation.isHoldExpiredAt(now)
                        ? ReservationStatus.EXPIRED
                        : ReservationStatus.CANCELLED;
                seatLockService.release(List.of(reservation), finalStatus, now);
            }
            case CONFIRMED -> refund(reservation, now);
            default -> throw new InvalidStateException("RESERVATION_CLOSED",
                    "La reserva " + reservationId + " ya está " + reservation.getStatus());
        }
        return ReservationResponse.from(reservation);
    }

    private void refund(Reservation reservation, Instant now) {
        if (!now.isBefore(reservation.getShowStartTime())) {
            throw new InvalidStateException("SHOW_ALREADY_STARTED",
                    "No se puede devolver una entrada de una sesión que ya ha comenzado");
        }
        boolean anyUsed = reservation.getSeats().stream().anyMatch(seat -> seat.getCheckedInAt() != null);
        if (anyUsed) {
            throw new InvalidStateException("TICKET_ALREADY_USED",
                    "No se puede devolver una reserva con entradas ya validadas en puerta");
        }

        // --- Comunicación síncrona (COMANDO) con la pasarela: devolución ---
        GatewayChargeResponse refund = paymentGateway.refund(new GatewayRefundRequest(
                reservation.getPaymentReference(), reservation.getTotalAmount(), reservation.getCurrency()));
        if (!refund.approved()) {
            throw new InvalidStateException("REFUND_REJECTED",
                    "La pasarela ha rechazado la devolución: " + refund.reason());
        }

        seatLockService.release(List.of(reservation), ReservationStatus.REFUNDED, now);
        outboxService.enqueuePaymentRefunded(reservation);
        log.info("Reserva {} devuelta ({} {})", reservation.getId(), reservation.getTotalAmount(), reservation.getCurrency());
    }
}
