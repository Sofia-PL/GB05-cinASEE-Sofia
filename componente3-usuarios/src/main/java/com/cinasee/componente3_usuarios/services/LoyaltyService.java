package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.events.PaymentCompletedEvent;

public interface LoyaltyService {

    /**
     * Procesa un pago completado: suma puntos al usuario y registra la compra
     * en su historial. La operación es idempotente por reservationId.
     */
    void processPaymentCompleted(PaymentCompletedEvent event);
}
