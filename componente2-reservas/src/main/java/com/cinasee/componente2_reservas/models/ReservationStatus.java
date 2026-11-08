package com.cinasee.componente2_reservas.models;

/**
 * Ciclo de vida de una reserva.
 *
 * <pre>
 *                  pago OK
 *  PENDING_PAYMENT ─────────► CONFIRMED ── devolución ──► REFUNDED  (libera butacas)
 *       │  │
 *       │  └── TTL de 10 min agotado ──► EXPIRED                    (libera butacas)
 *       └───── cancelación del usuario ─► CANCELLED                 (libera butacas)
 * </pre>
 *
 * Solo las reservas PENDING_PAYMENT (dentro de su TTL) y CONFIRMED ocupan
 * butacas. El resto son estados finales que se conservan como histórico.
 */
public enum ReservationStatus {
    /** Butacas bloqueadas temporalmente a la espera del pago. */
    PENDING_PAYMENT,
    /** Pago realizado y entradas emitidas. */
    CONFIRMED,
    /** Cancelada por el usuario antes de pagar. */
    CANCELLED,
    /** El bloqueo caducó (TTL) sin que se completase el pago. */
    EXPIRED,
    /** Pagada y posteriormente devuelta (anulación con reembolso). */
    REFUNDED
}
