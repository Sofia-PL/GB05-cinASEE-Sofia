package com.cinasee.componente2_reservas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (@Scheduled):
 * <ul>
 *   <li>Barrido de reservas caducadas (libera butacas pasados los 10 minutos).</li>
 *   <li>Relay del outbox (publica en RabbitMQ los eventos pendientes).</li>
 * </ul>
 *
 * Se puede desactivar con {@code app.scheduling.enabled=false}; los tests lo hacen
 * para ejecutar estas tareas manualmente y de forma determinista.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
