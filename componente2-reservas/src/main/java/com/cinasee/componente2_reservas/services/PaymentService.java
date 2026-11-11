package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.clients.dto.GatewayChargeRequest;
import com.cinasee.componente2_reservas.clients.dto.GatewayChargeResponse;
import com.cinasee.componente2_reservas.dto.PaymentRequest;
import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.events.OutboxService;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Pago de reservas y emisión de entradas.
 *
 * <p>Hay dos formas de cobrar, según {@code app.payment.mode}:</p>
 * <ul>
 *   <li><b>simulated</b> (tests y modo aislado): {@link #payReservation}, cobro síncrono
 *       en una sola petición (POST /reservations/{id}/payment).</li>
 *   <li><b>redsys</b> (despliegue completo): el cliente paga en la web de Redsys y el
 *       resultado llega después; lo gestiona {@link RedsysPaymentService}.</li>
 * </ul>
 * <p>Las dos terminan en {@link #confirmPaidReservation}: misma emisión de entradas y
 * mismo evento PaymentCompleted, así que los Componentes 3 y 4 no distinguen cómo se pagó.</p>
 *
 * <h2>Pasos del cobro síncrono (una sola transacción ACID)</h2>
 * <ol>
 *   <li>Bloquear la fila de la reserva (SELECT ... FOR UPDATE).</li>
 *   <li>Comprobar propietario, estado y TTL. Si el TTL ha vencido se marca EXPIRED,
 *       se liberan las butacas y se responde 400 ({@code noRollbackFor} guarda ese cambio).</li>
 *   <li><b>Reservas → Pasarela</b>: cobro (comunicación síncrona de tipo COMANDO).</li>
 *   <li>Si se aprueba: {@link #confirmPaidReservation}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationRepository reservationRepository;
    private final SeatLockService seatLockService;
    private final PaymentGateway paymentGateway;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final OutboxService outboxService;
    private final Clock clock;

    /** Cobro síncrono con la pasarela activa (solo admite cobros directos la simulada). */
    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public TicketConfirmation payReservation(Long reservationId, PaymentRequest request, CallerIdentity caller) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva " + reservationId + " no existe"));
        ReservationAccessPolicy.checkCanAccess(reservation, caller);

        Instant now = clock.instant();
        ensurePayable(reservation, now);

        // --- Comunicación síncrona (COMANDO) con la pasarela externa ---
        GatewayChargeResponse charge = paymentGateway.charge(new GatewayChargeRequest(
                buildOrderId(reservation.getId(), now),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                request.paymentToken()));

        if (!charge.approved()) {
            // La reserva sigue PENDING_PAYMENT: el cliente puede reintentar con otra tarjeta dentro del TTL
            log.info("Pago de la reserva {} rechazado: {}", reservationId, charge.reason());
            throw new PaymentDeclinedException(charge.reason() != null ? charge.reason() : "motivo no especificado");
        }

        return confirmPaidReservation(reservation, request.gateway().name(), charge.transactionId(), now);
    }

    /**
     * Marca como pagada una reserva PENDING_PAYMENT ya cobrada: butacas a SOLD, un
     * código QR único por butaca, reserva CONFIRMED y evento PaymentCompleted en el
     * outbox. Debe llamarse dentro de la transacción que tiene la reserva bloqueada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketConfirmation confirmPaidReservation(Reservation reservation, String gateway,
                                                     String paymentReference, Instant now) {
        List<ReservedSeat> seats = sortedSeats(reservation);
        for (ReservedSeat seat : seats) {
            seat.setStatus(SeatStatus.SOLD);
            seat.setTicketCode(ticketCodeGenerator.generate(reservation.getId(), seat.getSeatRow(), seat.getSeatColumn()));
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPaidAt(now);
        reservation.setPaymentGateway(gateway);
        reservation.setPaymentReference(paymentReference);
        reservation.setPaymentStartedAt(null); // ya no hay un pago en curso

        // --- Evento de dominio asíncrono (Transactional Outbox) ---
        outboxService.enqueuePaymentCompleted(reservation);

        log.info("Reserva {} pagada con {} ({} {}), {} entradas emitidas",
                reservation.getId(), gateway, reservation.getTotalAmount(), reservation.getCurrency(), seats.size());
        return toConfirmation(reservation);
    }

    /** Confirmación de una reserva ya pagada (también para respuestas repetidas de Redsys). */
    public static TicketConfirmation toConfirmation(Reservation reservation) {
        return new TicketConfirmation(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getPaymentReference(),
                sortedSeats(reservation).stream().map(ReservedSeat::getTicketCode).toList());
    }

    private static List<ReservedSeat> sortedSeats(Reservation reservation) {
        return reservation.getSeats().stream()
                .sorted(Comparator.comparing(ReservedSeat::getSeatRow).thenComparing(ReservedSeat::getSeatColumn))
                .toList();
    }

    /** Valida que la reserva puede pagarse ahora mismo. */
    private void ensurePayable(Reservation reservation, Instant now) {
        switch (reservation.getStatus()) {
            case CONFIRMED -> throw new InvalidStateException("ALREADY_PAID",
                    "La reserva " + reservation.getId() + " ya está pagada");
            case EXPIRED -> throw new ReservationExpiredException(reservation.getId());
            case CANCELLED, REFUNDED -> throw new InvalidStateException("RESERVATION_CLOSED",
                    "La reserva " + reservation.getId() + " está " + reservation.getStatus() + " y no se puede pagar");
            case PENDING_PAYMENT -> {
                if (reservation.isHoldExpiredAt(now)) {
                    // Expiración perezosa: el barrido aún no había pasado
                    seatLockService.release(List.of(reservation), ReservationStatus.EXPIRED, now);
                    throw new ReservationExpiredException(reservation.getId());
                }
            }
        }
    }

    /**
     * Número de pedido para la pasarela. Redsys exige 4-12 caracteres, los 4
     * primeros numéricos, y uno distinto por cada intento de cobro (rechaza los
     * repetidos): se combina el id de la reserva (8 dígitos) con 4 dígitos del
     * instante del intento.
     */
    static String buildOrderId(Long reservationId, Instant now) {
        return String.format("%08d%04d", reservationId % 100_000_000L, now.toEpochMilli() % 10_000L);
    }
}
