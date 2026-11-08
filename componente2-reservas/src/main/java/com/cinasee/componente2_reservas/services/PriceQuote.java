package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.models.DiscountType;

import java.math.BigDecimal;

/**
 * Resultado del cálculo de precio de una reserva.
 *
 * @param basePrice    precio unitario de la sesión antes de descuentos
 * @param discountType descuento aplicado (NONE si no hay)
 * @param discountRate porcentaje aplicado en tanto por uno (0.20 = 20 %)
 * @param unitPrice    precio unitario final, redondeado a 2 decimales
 * @param totalAmount  unitPrice x número de butacas
 */
public record PriceQuote(
        BigDecimal basePrice,
        DiscountType discountType,
        BigDecimal discountRate,
        BigDecimal unitPrice,
        BigDecimal totalAmount
) {
}
