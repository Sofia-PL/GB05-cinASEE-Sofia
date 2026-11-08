package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/** 400: el bloqueo temporal de 10 minutos ha caducado (definido así en la especificación). */
public class ReservationExpiredException extends ReservationApiException {
    public ReservationExpiredException(Long reservationId) {
        super(HttpStatus.BAD_REQUEST, "RESERVATION_EXPIRED",
                "El bloqueo de butacas de la reserva " + reservationId
                        + " ha caducado (TTL de 10 minutos). Inicia una nueva reserva.");
    }
}
