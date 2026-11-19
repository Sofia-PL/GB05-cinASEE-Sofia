package com.cinasee.componente2_reservas.repositories;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proyección JPQL (constructor expression) con las ventas agregadas de una sesión.
 *
 * @param ticketsSold  suma de butacas vendidas
 * @param revenue      suma de importes cobrados
 * @param reservations número de reservas confirmadas
 */
public record ShowSalesAggregate(
        String showId,
        String movieId,
        String roomId,
        Instant showStartTime,
        Long ticketsSold,
        BigDecimal revenue,
        Long reservations
) {
}
