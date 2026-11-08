package com.cinasee.componente2_reservas.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de la pasarela a un cobro o devolución.
 *
 * @param status        APPROVED o DECLINED
 * @param transactionId referencia de la operación en la pasarela
 * @param reason        motivo del rechazo (si lo hay)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayChargeResponse(
        String status,
        String transactionId,
        String reason
) {
    public boolean approved() {
        return "APPROVED".equalsIgnoreCase(status);
    }
}
