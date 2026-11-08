package com.cinasee.componente2_reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del sistema como bean.
 *
 * Toda la lógica de TTL usa {@code Instant.now(clock)} en lugar de
 * {@code Instant.now()}; así los tests pueden inyectar un reloj fijo y comprobar
 * la caducidad de los 10 minutos sin tener que esperar.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
