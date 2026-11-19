package com.cinasee.componente2_reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición para iniciar una reserva y bloquear butacas (POST /reservations).
 *
 * @param showId     sesión del Componente 1 (Cartelera). Obligatorio.
 * @param userId     usuario del Componente 3. Si es nulo y quien llama es un
 *                   cliente registrado, se usa el id de su token; si es un
 *                   invitado, la compra se vincula a {@code guestEmail}.
 * @param guestEmail correo del invitado (obligatorio cuando quien llama usa un
 *                   token de invitado, rol VISITANTE).
 * @param seats      entre 1 y 10 butacas.
 */
public record CreateReservationRequest(
        @NotBlank(message = "El identificador de la sesión (showId) es obligatorio")
        String showId,

        @Positive(message = "userId debe ser positivo")
        Long userId,

        @Email(message = "guestEmail no tiene un formato de correo válido")
        @Size(max = 150, message = "guestEmail no puede superar 150 caracteres")
        String guestEmail,

        @NotNull(message = "Debes seleccionar al menos una butaca")
        @Size(min = 1, max = 10, message = "Se pueden reservar entre 1 y 10 butacas")
        List<@Valid @NotNull SeatSelection> seats
) {
}
