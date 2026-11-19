package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.dto.SeatSelection;

import java.time.Instant;
import java.util.List;

/**
 * Datos ya validados (con Cartelera y Usuarios) necesarios para bloquear butacas.
 * Se construye en {@link ReservationService} y lo consume {@link SeatLockService}.
 */
public record HoldCommand(
        String showId,
        String movieId,
        String roomId,
        Instant showStartTime,
        Long userId,
        String guestEmail,
        String ownerSubject,
        List<SeatSelection> seats,
        PriceQuote quote,
        String currency
) {
}
