package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/**
 * 400: la respuesta de la pasarela no es válida (firma incorrecta, pedido o importe
 * que no coinciden, parámetros ausentes...). Nunca se confirma un pago con una
 * respuesta así: podría estar manipulada.
 */
public class InvalidPaymentResponseException extends ReservationApiException {
    public InvalidPaymentResponseException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_RESPONSE", message);
    }
}
