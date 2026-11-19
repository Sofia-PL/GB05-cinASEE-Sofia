package com.cinasee.componente2_reservas.repositories;

import java.math.BigDecimal;

/**
 * Proyección JPQL con las ventas agregadas de una película (informe Box Office).
 */
public record MovieSalesAggregate(
        String movieId,
        Long ticketsSold,
        BigDecimal grossRevenue,
        Long reservations
) {
}
