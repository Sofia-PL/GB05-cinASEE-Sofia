package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.models.OutboxEvent;
import com.cinasee.componente2_reservas.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Proceso que vacía la tabla outbox publicando los eventos en RabbitMQ.
 *
 * Cada pocos segundos ({@code app.outbox.relay-interval}, 2 s por defecto):
 * <ol>
 *   <li>Lee hasta N eventos sin publicar, por orden de creación.</li>
 *   <li>Los publica uno a uno esperando la confirmación del broker.</li>
 *   <li>Marca cada evento como publicado. Si uno falla (broker caído), se anota
 *       el error, se detiene el lote y se reintenta en la siguiente pasada.</li>
 * </ol>
 *
 * Si el servicio se cae entre "publicar" y "marcar como publicado", el evento se
 * volverá a enviar: la entrega es "al menos una vez" y los consumidores deben ser
 * idempotentes (el Comp. 3 descarta duplicados por reservationId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval:2s}")
    public void scheduledRelay() {
        publishPendingEvents();
    }

    /**
     * @return número de eventos publicados en esta pasada
     */
    public int publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPending(PageRequest.of(0, batchSize));
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                eventPublisher.publish(event);
                event.setPublishedAt(clock.instant());
                event.setLastError(null);
                outboxEventRepository.save(event);
                published++;
                log.info("Evento {} {} publicado (reserva {})", event.getEventType(), event.getId(), event.getAggregateId());
            } catch (RuntimeException e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage()));
                outboxEventRepository.save(event);
                log.warn("No se pudo publicar el evento {} (intento {}): {}. Se reintentará.",
                        event.getId(), event.getAttempts(), e.getMessage());
                // Lo más probable es que el broker esté caído: no tiene sentido seguir con el lote
                break;
            }
        }
        return published;
    }

    private static String truncate(String message) {
        if (message == null) {
            return "error desconocido";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
