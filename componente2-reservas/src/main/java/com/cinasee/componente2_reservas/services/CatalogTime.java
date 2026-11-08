package com.cinasee.componente2_reservas.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Conversión tolerante de las fechas que devuelve el Componente 1.
 *
 * La especificación OpenAPI define las fechas con zona ("2026-10-15T18:30:00Z"),
 * pero la implementación actual de Cartelera usa LocalDateTime y las serializa
 * sin zona ("2026-10-15T18:30:00"). Aceptamos ambos formatos para no acoplarnos a
 * un detalle interno del otro servicio.
 */
public final class CatalogTime {

    private CatalogTime() {
    }

    /**
     * @param value        fecha en texto ISO-8601, con o sin zona
     * @param fallbackZone zona que se asume cuando el texto no la incluye
     * @return el instante, o {@code null} si el texto es nulo o no se puede interpretar
     */
    public static Instant toInstant(String value, ZoneId fallbackZone) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // No trae zona: se intenta como fecha local
        }
        try {
            return LocalDateTime.parse(value).atZone(fallbackZone).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
