package com.cinasee.componente2_reservas.dto;

import com.cinasee.componente2_reservas.models.SeatStatus;

import java.util.List;

/**
 * Butacas ocupadas de una sesión (GET /reservations/shows/{showId}/seats).
 * El frontend combina esta lista con las dimensiones de la sala (Comp. 1) para
 * pintar el mapa interactivo: lo que no aparece aquí está libre.
 */
public record SeatMapResponse(String showId, List<OccupiedSeat> occupiedSeats) {

    /** HELD = bloqueada temporalmente por otra persona; SOLD = vendida. */
    public record OccupiedSeat(Integer row, Integer column, SeatStatus status) {
    }
}
