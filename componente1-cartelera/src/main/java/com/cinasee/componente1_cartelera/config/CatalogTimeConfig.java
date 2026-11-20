package com.cinasee.componente1_cartelera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del catálogo. Las horas de las sesiones se guardan como LocalDateTime sin zona, en
 * la hora local del cine; el contenedor se ejecuta en UTC. Para saber si una sesión ya ha
 * empezado o terminado hay que comparar con "ahora" en esa misma zona
 * (CATALOG_TIME_ZONE, igual que en los Componentes 2 y 4).
 */
@Configuration
public class CatalogTimeConfig {

    @Bean
    public Clock catalogClock(@Value("${app.catalog.time-zone:Europe/Madrid}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
