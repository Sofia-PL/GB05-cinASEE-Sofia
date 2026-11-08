package com.cinasee.componente2_reservas.models;

/**
 * Tarifas especiales que puede aplicar el motor de precios.
 * Solo se aplica el descuento más ventajoso para el cliente (no son acumulables).
 */
public enum DiscountType {
    /** Sin descuento: se cobra el precio base de la sesión. */
    NONE,
    /** Día del espectador (por defecto, los miércoles). */
    SPECTATOR_DAY,
    /** Cliente del programa de fidelización con saldo de puntos suficiente. */
    LOYALTY
}
