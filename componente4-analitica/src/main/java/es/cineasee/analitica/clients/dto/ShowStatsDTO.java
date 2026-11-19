package es.cineasee.analitica.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entradas vendidas por sesión, tal y como las devuelve el Componente 2 en
 * {@code GET /reservations/stats}.
 *
 * <p>Es la fuente principal para calcular la ocupación. El aforo no viene aquí:
 * se obtiene del Componente 1 (Cartelera), que es el dueño de las salas.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowStatsDTO(
        String showId,
        String movieId,
        String roomId,
        Instant showStartTime,
        long ticketsSold,
        long reservations,
        BigDecimal revenue,
        String currency
) {
}
