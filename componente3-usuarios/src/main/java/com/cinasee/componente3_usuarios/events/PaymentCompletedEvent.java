package com.cinasee.componente3_usuarios.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Evento de dominio emitido por el Componente 2 (Reservas y Pagos) cuando un
 * pago se confirma correctamente.
 *
 * Contrato acordado (JSON):
 * <pre>
 * {
 *   "eventId": "3f2c7a1e-...",
 *   "reservationId": 8901,
 *   "userId": 42,
 *   "movieId": "550e8400-e29b-41d4-a716-446655440001",
 *   "amount": 18.50,
 *   "currency": "EUR",
 *   "purchaseDate": "2026-09-20T18:30:00Z",
 *   "occurredAt": "2026-09-20T18:30:05Z"
 * }
 * </pre>
 *
 * userId puede ser null en compras de invitado: en ese caso no se asignan
 * puntos ni se guarda historial, porque no hay usuario al que asociarlo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
        String eventId,
        Long reservationId,
        Long userId,
        String movieId,
        BigDecimal amount,
        String currency,
        OffsetDateTime purchaseDate,
        OffsetDateTime occurredAt
) {
}
