package com.cinasee.componente2_reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entradas vendidas por sesión (GET /reservations/stats).
 *
 * Lo consume el Componente 4 (Analítica) para calcular la tasa de ocupación:
 * ocupación = ticketsSold / aforo de la sala (que obtiene del Componente 1).
 * Con showStartTime puede filtrar por día de la semana y franja horaria.
 */
public record ShowStatsResponse(
        String showId,
        String movieId,
        String roomId,
        Instant showStartTime,
        long ticketsSold,
        long reservations,
        BigDecimal revenue,
        String currency
) {
}
