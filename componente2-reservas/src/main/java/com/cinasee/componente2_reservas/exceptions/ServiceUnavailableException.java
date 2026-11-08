package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/**
 * 503: un servicio del que dependemos (Cartelera, Usuarios o la pasarela de pago)
 * no responde. La operación no se ha completado y puede reintentarse.
 */
public class ServiceUnavailableException extends ReservationApiException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message, cause);
    }
}
