package com.cinasee.componente2_reservas.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Vista local del perfil que devuelve el Componente 3 en GET /users/{userId}
 * (schema UserProfile). Reservas solo usa el rol y el saldo de puntos para
 * decidir si aplica el descuento de fidelización.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileDto(
        Long id,
        String email,
        String fullName,
        String role,
        Integer loyaltyPoints,
        List<String> preferredGenres
) {
}
