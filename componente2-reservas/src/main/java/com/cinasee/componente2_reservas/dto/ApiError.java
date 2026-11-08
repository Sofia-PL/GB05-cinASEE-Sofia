package com.cinasee.componente2_reservas.dto;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo JSON estándar de todas las respuestas de error del microservicio.
 *
 * @param code    código corto y estable (SEATS_UNAVAILABLE, RESERVATION_EXPIRED...)
 * @param details información adicional (campos inválidos, butacas en conflicto...)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<String> details,
        String path
) {
}
