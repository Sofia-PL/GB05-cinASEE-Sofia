package com.cinasee.componente2_reservas.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Identidad de quien hace la petición, extraída del JWT ya validado.
 *
 * Desacopla la lógica de negocio de Spring Security: los servicios reciben este
 * record y no un objeto {@link Jwt}, lo que además simplifica los tests.
 *
 * @param subject claim "sub": id numérico del usuario o "guest:&lt;uuid&gt;" en invitados
 * @param role    claim "role": VISITANTE, CLIENTE_REGISTRADO, STAFF_TAQUILLA o ADMIN
 */
public record CallerIdentity(String subject, String role) {

    public static final String ROLE_GUEST = "VISITANTE";
    public static final String ROLE_CLIENT = "CLIENTE_REGISTRADO";
    public static final String ROLE_STAFF = "STAFF_TAQUILLA";
    public static final String ROLE_ADMIN = "ADMIN";

    public static CallerIdentity from(Jwt jwt) {
        return new CallerIdentity(jwt.getSubject(), jwt.getClaimAsString("role"));
    }

    /** Personal interno: taquilla o administración. Puede operar sobre cualquier reserva. */
    public boolean isStaff() {
        return ROLE_STAFF.equals(role) || ROLE_ADMIN.equals(role);
    }

    /** Invitado con token temporal del Componente 3 (POST /auth/guest). */
    public boolean isGuest() {
        return ROLE_GUEST.equals(role);
    }

    public boolean isRegisteredClient() {
        return ROLE_CLIENT.equals(role);
    }

    /**
     * Id de usuario del Componente 3 si el "sub" es numérico (usuarios registrados).
     * Vacío para invitados ("guest:...").
     */
    public Optional<Long> userId() {
        try {
            return Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
