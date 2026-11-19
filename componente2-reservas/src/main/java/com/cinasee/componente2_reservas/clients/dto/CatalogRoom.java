package com.cinasee.componente2_reservas.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Vista local de la sala que devuelve el Componente 1 en GET /rooms/{id}.
 * Reservas la usa para comprobar que las butacas pedidas existen en el mapa
 * (fila ≤ rowsCount, columna ≤ columnsCount) y como precio de respaldo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogRoom(
        String id,
        String name,
        String roomType,
        Integer capacity,
        Integer rowsCount,
        Integer columnsCount,
        Boolean wheelchairAccessible,
        BigDecimal basePrice
) {
}
