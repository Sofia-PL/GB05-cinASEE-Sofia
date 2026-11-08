package com.cinasee.componente2_reservas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición de pago de una reserva (POST /reservations/{id}/payment).
 *
 * @param gateway      pasarela de pago; la especificación solo admite REDSYS.
 * @param paymentToken token generado por el frontend al tokenizar la tarjeta.
 *                     Este servicio nunca recibe ni guarda datos de tarjeta.
 */
public record PaymentRequest(
        @NotNull(message = "La pasarela de pago es obligatoria")
        PaymentGatewayType gateway,

        @NotBlank(message = "El token de pago es obligatorio")
        @Size(max = 255, message = "El token de pago no puede superar 255 caracteres")
        String paymentToken
) {
    /** Pasarelas admitidas (enum del OpenAPI). */
    public enum PaymentGatewayType {
        REDSYS
    }
}
