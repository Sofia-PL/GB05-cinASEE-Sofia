package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/** 400: la petición es sintácticamente válida pero incumple una regla de negocio. */
public class InvalidReservationException extends ReservationApiException {
    public InvalidReservationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION", message);
    }
}
