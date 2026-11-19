package es.cineasee.analitica.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos de taquilla por película en un periodo, tal y como los devuelve el
 * Componente 2 en {@code GET /reservations/box-office-data}.
 *
 * <p>El Componente 2 solo conoce sus propios datos (entradas e importes). El
 * título y la distribuidora se resuelven consultando el catálogo del
 * Componente 1.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoxOfficeDataDTO(
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        List<MovieBoxOfficeDTO> movies
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieBoxOfficeDTO(
            String movieId,
            long totalTicketsSold,
            long reservations,
            BigDecimal grossRevenue
    ) {
    }
}
