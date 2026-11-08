package com.cinasee.componente2_reservas.dto;

import com.cinasee.componente2_reservas.models.ReservationStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta del pago (schema TicketConfirmation del OpenAPI).
 *
 * @param tickets códigos únicos de las entradas, uno por butaca. Es el contenido
 *                que el frontend codifica en el QR / PKPass.
 */
public record TicketConfirmation(
        Long reservationId,
        ReservationStatus status,
        BigDecimal totalAmount,
        String currency,
        String paymentReference,
        List<String> tickets
) {
}
