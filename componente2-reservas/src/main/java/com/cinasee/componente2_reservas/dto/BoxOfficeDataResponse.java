package com.cinasee.componente2_reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos de taquilla por película en un periodo (GET /reservations/box-office-data).
 *
 * Lo consume el Componente 4 para el informe Box Office. El filtrado por
 * distribuidora lo hace Analítica cruzando movieId con el Componente 1, porque la
 * distribuidora es un dato del catálogo, no de Reservas.
 */
public record BoxOfficeDataResponse(
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        List<MovieBoxOffice> movies
) {
    public record MovieBoxOffice(String movieId, long totalTicketsSold, long reservations, BigDecimal grossRevenue) {
    }
}
