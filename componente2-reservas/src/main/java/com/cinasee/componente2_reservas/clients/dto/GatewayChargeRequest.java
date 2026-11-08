package com.cinasee.componente2_reservas.clients.dto;

import java.math.BigDecimal;

/**
 * Orden de cobro enviada a la pasarela.
 *
 * @param orderId      identificador de pedido único (Redsýs exige uno por operación)
 * @param amount       importe a cobrar
 * @param paymentToken token de la tarjeta generado en el frontend
 */
public record GatewayChargeRequest(
        String orderId,
        BigDecimal amount,
        String currency,
        String paymentToken
) {
}
