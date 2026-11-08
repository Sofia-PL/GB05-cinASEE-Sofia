package com.cinasee.componente2_reservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.ZoneId;

/**
 * Tarifas especiales (prefijo {@code app.pricing}).
 *
 * @param spectatorDay           día del espectador
 * @param spectatorDayDiscount   descuento del día del espectador (0.20 = 20 %)
 * @param loyaltyPointsThreshold puntos mínimos del monedero para el descuento de fidelización
 * @param loyaltyDiscount        descuento de fidelización (0.10 = 10 %)
 * @param timeZone               zona horaria de los cines, para saber qué día de la semana es la sesión
 */
@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(
        @DefaultValue("WEDNESDAY") DayOfWeek spectatorDay,
        @DefaultValue("0.20") BigDecimal spectatorDayDiscount,
        @DefaultValue("500") int loyaltyPointsThreshold,
        @DefaultValue("0.10") BigDecimal loyaltyDiscount,
        @DefaultValue("Europe/Madrid") ZoneId timeZone
) {
}
