package com.cinasee.componente2_reservas.clients;

import com.cinasee.componente2_reservas.clients.dto.CatalogRoom;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente HTTP declarativo (OpenFeign) hacia el Componente 1 - Cartelera.
 *
 * COMUNICACIÓN SÍNCRONA de tipo CONSULTA. Reservas nunca lee la base de datos
 * MongoDB de Cartelera: pregunta a su API REST.
 *
 * URL base configurable con {@code app.clients.cartelera.url}
 * (por defecto http://localhost:8081/api/v1).
 *
 * Usa {@link ForwardJwtFeignConfig} para reenviar el JWT del usuario, porque
 * Cartelera exige autenticación en estos endpoints.
 */
@FeignClient(
        name = "cartelera",
        url = "${app.clients.cartelera.url}",
        configuration = ForwardJwtFeignConfig.class
)
public interface CarteleraClient {

    /** Reservas -> Cartelera -> GET /sessions/{sessionId}: estado del pase, sala y precio. */
    @GetMapping("/sessions/{sessionId}")
    CatalogSession getSession(@PathVariable("sessionId") String sessionId);

    /** Reservas -> Cartelera -> GET /rooms/{roomId}: dimensiones de la sala para validar butacas. */
    @GetMapping("/rooms/{roomId}")
    CatalogRoom getRoom(@PathVariable("roomId") String roomId);
}
