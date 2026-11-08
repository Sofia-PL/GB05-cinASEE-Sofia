package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.config.MessagingProperties;
import com.cinasee.componente2_reservas.config.PricingProperties;
import com.cinasee.componente2_reservas.models.OutboxEvent;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registra eventos de dominio en la tabla outbox (patrón Transactional Outbox).
 *
 * Los métodos exigen una transacción abierta (Propagation.MANDATORY): el evento
 * debe guardarse en la MISMA transacción que el cambio de estado de la reserva.
 * Si la transacción hace rollback, el evento desaparece con ella; si hace commit,
 * el {@link OutboxRelay} lo publicará en RabbitMQ.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagingProperties messagingProperties;
    /** Se usa su zona horaria (la de los cines) para el campo "timestamp" del evento. */
    private final PricingProperties pricingProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Encola PaymentCompleted para una reserva recién confirmada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentCompletedEvent enqueuePaymentCompleted(Reservation reservation) {
        Instant now = clock.instant();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMovieId(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getPaidAt(),
                now,
                reservation.getShowId(),
                reservation.getShowId(),            // sessionId: nombre que usa el Comp. 4
                reservation.getRoomId(),
                reservation.getSeatCount(),
                reservation.getTotalAmount(),       // totalAmount: nombre que usa el Comp. 4
                reservation.getShowStartTime(),
                // timestamp: hora local de la sesión, para el día de la semana y la franja (Comp. 4)
                LocalDateTime.ofInstant(reservation.getShowStartTime(), pricingProperties.timeZone()));
        save(event.eventId(), PaymentCompletedEvent.TYPE, messagingProperties.paymentCompletedRoutingKey(),
                reservation.getId(), event, now);
        return event;
    }

    /** Encola PaymentRefunded para una reserva devuelta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentRefundedEvent enqueuePaymentRefunded(Reservation reservation) {
        Instant now = clock.instant();
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMovieId(),
                reservation.getShowId(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getSeatCount(),
                reservation.getClosedAt(),
                now);
        save(event.eventId(), PaymentRefundedEvent.TYPE, messagingProperties.paymentRefundedRoutingKey(),
                reservation.getId(), event, now);
        return event;
    }

    private void save(String eventId, String type, String routingKey, Long reservationId, Object payload, Instant now) {
        outboxEventRepository.save(OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Reservation")
                .aggregateId(String.valueOf(reservationId))
                .eventType(type)
                .routingKey(routingKey)
                // Jackson 3 serializa Instant en ISO-8601 ("2026-10-08T12:04:36Z")
                .payload(jsonMapper.writeValueAsString(payload))
                .createdAt(now)
                .attempts(0)
                .build());
    }
}
