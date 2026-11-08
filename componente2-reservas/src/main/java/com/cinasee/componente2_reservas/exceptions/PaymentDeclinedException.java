package com.cinasee.componente2_reservas.exceptions;

import org.springframework.http.HttpStatus;

/** 402: la pasarela (Redsýs) ha rechazado el cobro. */
public class PaymentDeclinedException extends ReservationApiException {
    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED",
                "Pago rechazado por la entidad bancaria: " + reason);
    }
}
