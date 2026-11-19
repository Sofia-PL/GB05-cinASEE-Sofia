package com.cinasee.componente2_reservas.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Evento de dominio PaymentCompleted, publicado en RabbitMQ tras confirmar un pago.
 *
 * <p>Un mismo mensaje lo consumen DOS componentes, cada uno con su propia clase
 * Java y sus propios nombres de campo. Para no obligar a ninguno a cambiar, el
 * JSON incluye los campos que espera cada uno (los que no conoce, los ignora):</p>
 *
 * <ul>
 *   <li><b>Componente 3 (Usuarios)</b>, cola {@code user.payment.completed}:
 *       {@code eventId, reservationId, userId, movieId, amount, currency,
 *       purchaseDate, occurredAt}. Suma puntos y guarda el historial.</li>
 *   <li><b>Componente 4 (Analítica)</b>, cola {@code payment.completed.queue}:
 *       {@code reservationId, userId, roomId, sessionId, movieId, ticketsCount,
 *       totalAmount, timestamp}. Guarda el hecho de venta para ocupación y taquilla.</li>
 * </ul>
 *
 * <p>Algunos datos aparecen dos veces con distinto nombre a propósito
 * ({@code amount}/{@code totalAmount}, {@code showId}/{@code sessionId}).
 * {@code timestamp} es la hora de inicio de la sesión en hora local del cine
 * (LocalDateTime, sin zona), porque Analítica la usa para calcular el día de la
 * semana y la franja horaria de la sesión.</p>
 *
 * @param userId nulo en compras de invitado (el Componente 3 no asigna puntos)
 */
public record PaymentCompletedEvent(
        // --- Contrato del Componente 3 ---
        String eventId,
        Long reservationId,
        Long userId,
        String movieId,
        BigDecimal amount,
        String currency,
        Instant purchaseDate,
        Instant occurredAt,
        // --- Campos adicionales (Componente 4) ---
        String showId,
        String sessionId,
        String roomId,
        Integer ticketsCount,
        BigDecimal totalAmount,
        Instant showStartTime,
        LocalDateTime timestamp
) {
    public static final String TYPE = "PaymentCompleted";
}
