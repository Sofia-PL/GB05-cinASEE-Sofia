package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/** 404: la reserva, la sesión (Comp. 1), el usuario (Comp. 3) o la entrada no existen. */
public class ResourceNotFoundException extends ReservationApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
