package com.cinasee.componente2_reservas.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Evento de dominio pendiente de publicar en RabbitMQ (patrón Transactional Outbox).
 *
 * <p>Problema que resuelve: al confirmar un pago hay que (1) guardar la reserva
 * como CONFIRMED en PostgreSQL y (2) publicar PaymentCompleted en RabbitMQ.
 * Son dos sistemas distintos y no comparten transacción: si se publicase
 * directamente y la BD hiciese rollback, los Componentes 3 y 4 recibirían un
 * pago que no existe; si se guardase y RabbitMQ estuviese caído, el evento se
 * perdería.</p>
 *
 * <p>Solución: el evento se inserta en esta tabla <b>dentro de la misma
 * transacción ACID</b> que confirma la reserva. Un proceso programado
 * ({@code OutboxRelay}) lee después las filas no publicadas y las envía al broker,
 * reintentando si falla. Garantía resultante: entrega "al menos una vez"
 * (los consumidores deben ser idempotentes; el Componente 3 ya lo es por
 * reservationId).</p>
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_unpublished", columnList = "published_at, created_at")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    /** UUID del evento; viaja también como eventId y como messageId de AMQP. */
    @Id
    @Column(length = 36)
    private String id;

    /** Tipo de agregado que originó el evento (p. ej. "Reservation"). */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    /** Nombre del evento: PaymentCompleted, PaymentRefunded... */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Routing key con la que se publicará en el exchange topic. */
    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    /** Cuerpo JSON del mensaje, ya serializado. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Nulo mientras el evento esté pendiente de publicar. */
    @Column(name = "published_at")
    private Instant publishedAt;

    /** Número de intentos de publicación fallidos. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
