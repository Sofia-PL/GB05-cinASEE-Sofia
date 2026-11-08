package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/** 403: el usuario autenticado no puede actuar sobre este recurso. */
public class ForbiddenOperationException extends ReservationApiException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
