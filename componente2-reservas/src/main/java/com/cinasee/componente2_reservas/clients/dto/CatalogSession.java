package com.cinasee.componente2_reservas.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Vista local de la sesión que devuelve el Componente 1 en GET /sessions/{id}.
 *
 * Es una copia mínima del contrato (solo lo que Reservas necesita), NO la
 * entidad del otro servicio: cada microservicio tiene su propio modelo.
 * Los campos desconocidos se ignoran para que Cartelera pueda evolucionar sin
 * romper a Reservas.
 *
 * Las fechas se reciben como texto porque Cartelera las guarda como
 * LocalDateTime (sin zona) mientras que el OpenAPI las define con zona ("...Z");
 * se interpretan de forma tolerante en {@code CatalogTime}.
 *
 * @param status PROGRAMADA, A_LA_VENTA, EN_CURSO, FINALIZADA o CANCELADA
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSession(
        String id,
        String movieId,
        String roomId,
        String startTime,
        String endTime,
        String status,
        BigDecimal ticketPrice
) {
    /** Único estado en el que se permite vender entradas. */
    public static final String ON_SALE = "A_LA_VENTA";
}
