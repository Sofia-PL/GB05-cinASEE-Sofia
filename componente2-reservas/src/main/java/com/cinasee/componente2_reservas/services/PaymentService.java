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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Caso de uso: pagar una reserva y emitir las entradas
 * (POST /reservations/{reservationId}/payment).
 *
 * <h2>Pasos (una sola transacción ACID)</h2>
 * <ol>
 *   <li>Bloquear la fila de la reserva (SELECT ... FOR UPDATE): mientras se cobra,
 *       nadie más puede caducarla, cancelarla ni pagarla dos veces.</li>
 *   <li>Comprobar propietario, estado y TTL. Si el TTL ha vencido se marca EXPIRED,
 *       se liberan las butacas y se responde 400 (ese cambio SÍ se guarda gracias a
 *       {@code noRollbackFor}).</li>
 *   <li><b>Reservas → Pasarela externa</b>: POST /charge (comunicación síncrona de tipo COMANDO).</li>
 *   <li>Si se aprueba: butacas a SOLD, un código QR único por butaca, reserva CONFIRMED.</li>
 *   <li>Registrar el evento PaymentCompleted en el outbox, en la misma transacción.
 *       El relay lo publicará en RabbitMQ para los Componentes 3 (puntos) y 4 (métricas).</li>
 * </ol>
 *
 * Nota de diseño: la llamada a la pasarela se hace con la fila bloqueada. Es
 * deliberado (garantiza que no se cobra una reserva que otro proceso está
 * caducando) y está acotado por los timeouts de Feign.
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

    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public TicketConfirmation payReservation(Long reservationId, PaymentRequest request, CallerIdentity caller) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva " + reservationId + " no existe"));
        ReservationAccessPolicy.checkCanAccess(reservation, caller);

        Instant now = clock.instant();
        ensurePayable(reservation, now);

        // --- Comunicación síncrona (COMANDO) con la pasarela externa ---
        GatewayChargeResponse charge = paymentGateway.charge(new GatewayChargeRequest(
                buildOrderId(reservation, now),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                request.paymentToken()));

        if (!charge.approved()) {
            // La reserva sigue PENDING_PAYMENT: el cliente puede reintentar con otra tarjeta dentro del TTL
            log.info("Pago de la reserva {} rechazado: {}", reservationId, charge.reason());
            throw new PaymentDeclinedException(charge.reason() != null ? charge.reason() : "motivo no especificado");
        }

        // --- Emisión de entradas ---
        List<ReservedSeat> seats = reservation.getSeats().stream()
                .sorted(Comparator.comparing(ReservedSeat::getSeatRow).thenComparing(ReservedSeat::getSeatColumn))
                .toList();
        for (ReservedSeat seat : seats) {
            seat.setStatus(SeatStatus.SOLD);
            seat.setTicketCode(ticketCodeGenerator.generate(reservation.getId(), seat.getSeatRow(), seat.getSeatColumn()));
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPaidAt(now);
        reservation.setPaymentGateway(request.gateway().name());
        reservation.setPaymentReference(charge.transactionId());

        // --- Evento de dominio asíncrono (Transactional Outbox) ---
        outboxService.enqueuePaymentCompleted(reservation);

        log.info("Reserva {} pagada ({} {}), {} entradas emitidas",
                reservationId, reservation.getTotalAmount(), reservation.getCurrency(), seats.size());

        return new TicketConfirmation(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getPaymentReference(),
                seats.stream().map(ReservedSeat::getTicketCode).toList());
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
     * Número de pedido para la pasarela. Redsýs exige 4-12 caracteres, los 4
     * primeros numéricos, y uno distinto por cada intento de cobro: se combina el
     * id de la reserva (8 dígitos) con 4 dígitos del instante del intento.
     */
    private String buildOrderId(Reservation reservation, Instant now) {
        return String.format("%08d%04d", reservation.getId() % 100_000_000L, now.toEpochMilli() % 10_000L);
    }
}
