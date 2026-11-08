package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/**
 * 409: la operación no es posible en el estado actual del recurso
 * (p. ej. pagar una reserva ya pagada, reservar en una sesión que no está
 * "A la venta" o validar dos veces la misma entrada).
 */
public class InvalidStateException extends ReservationApiException {
    public InvalidStateException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
