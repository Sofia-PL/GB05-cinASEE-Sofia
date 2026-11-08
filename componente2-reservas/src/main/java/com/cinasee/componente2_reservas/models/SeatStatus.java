package com.cinasee.componente2_reservas.models;

/**
 * Estado de una butaca concreta dentro de una sesión.
 */
public enum SeatStatus {
    /** Bloqueada temporalmente por una reserva pendiente de pago (TTL). */
    HELD,
    /** Vendida: la reserva se ha pagado y existe una entrada emitida. */
    SOLD
}
