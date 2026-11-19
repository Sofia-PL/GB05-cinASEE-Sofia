package com.cinasee.componente2_reservas.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de dominio PaymentRefunded: una reserva pagada se ha anulado y devuelto.
 *
 * Se publica con la routing key "payment.refunded" en el mismo exchange. A día de
 * hoy ningún componente lo consume (el Comp. 3 solo enlaza "payment.completed"),
 * pero queda disponible para que Analítica descuente la recaudación o Usuarios
 * retire los puntos, sin tener que tocar este microservicio.
 */
public record PaymentRefundedEvent(
        String eventId,
        Long reservationId,
        Long userId,
        String movieId,
        String showId,
        BigDecimal amount,
        String currency,
        Integer ticketsCount,
        Instant refundedAt,
        Instant occurredAt
) {
    public static final String TYPE = "PaymentRefunded";
}
