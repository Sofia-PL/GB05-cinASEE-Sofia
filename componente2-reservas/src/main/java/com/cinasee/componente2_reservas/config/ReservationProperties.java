package com.cinasee.componente2_reservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Parámetros de negocio de las reservas (prefijo {@code app.reservations}).
 *
 * @param holdTtl         tiempo que permanecen bloqueadas las butacas sin pagar (TTL, 10 min)
 * @param currency        moneda en la que se cobra
 * @param expirationBatch reservas caducadas que libera el barrido en cada pasada
 * @param catalogTimeZone zona horaria con la que se interpretan las fechas sin zona
 *                        que devuelve Cartelera (guarda LocalDateTime)
 */
@ConfigurationProperties(prefix = "app.reservations")
public record ReservationProperties(
        @DefaultValue("10m") Duration holdTtl,
        @DefaultValue("EUR") String currency,
        @DefaultValue("100") int expirationBatch,
        @DefaultValue("UTC") ZoneId catalogTimeZone
) {
}
