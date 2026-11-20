package com.cinasee.componente1_cartelera.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Tarea periódica que pone las sesiones EN_CURSO al llegar su hora de inicio y FINALIZADA al
 * llegar su hora de fin. La lógica está en SessionService.actualizarEstadosPorHora.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStatusScheduler {

    private final SessionService sessionService;
    private final Clock catalogClock;

    @Scheduled(fixedDelayString = "${app.sessions.status-interval:60s}")
    public void actualizarEstados() {
        int cambiadas = sessionService.actualizarEstadosPorHora(LocalDateTime.now(catalogClock));
        if (cambiadas > 0) {
            log.info("Estado de {} sesiones actualizado según su hora de inicio o fin.", cambiadas);
        }
    }
}
