package com.cinasee.componente2_reservas.clients.dto;

import java.math.BigDecimal;

/**
 * Orden de devolución de un cobro previo.
 *
 * @param transactionId referencia del cobro original devuelta por la pasarela
 */
public record GatewayRefundRequest(
        String transactionId,
        BigDecimal amount,
        String currency
) {
}
