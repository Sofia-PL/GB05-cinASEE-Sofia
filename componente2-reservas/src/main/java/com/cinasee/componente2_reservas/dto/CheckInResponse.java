package com.cinasee.componente2_reservas.dto;

import java.time.Instant;

/**
 * Resultado de validar una entrada en la puerta de la sala (Portal de validación).
 */
public record CheckInResponse(
        String ticketCode,
        Long reservationId,
        Long showId,
        Long roomId,
        Integer row,
        Integer column,
        Instant showStartTime,
        Instant checkedInAt
) {
}
