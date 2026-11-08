package com.cinasee.componente2_reservas.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico de reservas caducadas (TTL de 10 minutos).
 *
 * Cada {@code app.reservations.expiration-sweep-interval} (30 s por defecto)
 * marca como EXPIRED las reservas pendientes cuyo bloqueo ha vencido y libera sus
 * butacas. Así, aunque nadie vuelva a reservar en esa sesión, las butacas
 * abandonadas aparecen como libres en el mapa como mucho 30 s después del TTL.
 *
 * Es seguro ejecutarlo en varias instancias a la vez: cada reserva se procesa con
 * SELECT ... FOR UPDATE y solo cambia si sigue en PENDING_PAYMENT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final SeatLockService seatLockService;

    @Scheduled(fixedDelayString = "${app.reservations.expiration-sweep-interval:30s}")
    public void expireOverdueReservations() {
        try {
            int expired = seatLockService.expireOverdueReservations();
            if (expired > 0) {
                log.info("Barrido TTL: {} reservas caducadas y sus butacas liberadas", expired);
            }
        } catch (RuntimeException e) {
            // Nunca dejamos que una excepción detenga el planificador; se reintenta en la siguiente pasada
            log.warn("Fallo en el barrido de reservas caducadas: {}", e.getMessage());
        }
    }
}
