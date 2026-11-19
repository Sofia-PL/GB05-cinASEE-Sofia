package es.cineasee.analitica.clients;

import es.cineasee.analitica.clients.dto.BoxOfficeDataDTO;
import es.cineasee.analitica.clients.dto.ShowStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Cliente Feign hacia el Componente 2 (Motor de Reservas y Pagos).
 *
 * <p>Analítica no accede a la base de datos de Reservas: consume sus endpoints
 * agregados, que son la fuente de verdad de las ventas.</p>
 *
 * <ul>
 *   <li>{@code GET /reservations/stats} → entradas vendidas por sesión
 *       (para la tasa de ocupación).</li>
 *   <li>{@code GET /reservations/box-office-data} → taquilla por película
 *       (para el informe Box Office).</li>
 * </ul>
 *
 * <p>Ambos endpoints exigen rol {@code ADMIN} o {@code STAFF_TAQUILLA}.
 * {@link ForwardJwtFeignConfig} propaga el token del usuario que llamó a
 * Analítica, de modo que Reservas aplica su propia política de acceso.</p>
 *
 * <p>El {@code context-path} del Componente 2 es {@code /v1}, por eso la URL
 * base termina en {@code :8082/v1}.</p>
 */
@FeignClient(
        name = "reservas-service",
        url = "${services.reservas.url:http://localhost:8082/v1}",
        configuration = ForwardJwtFeignConfig.class
)
public interface ReservasClient {

    @GetMapping("/reservations/stats")
    List<ShowStatsDTO> getShowStats(
            @RequestParam(value = "showId", required = false) String showId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    );

    @GetMapping("/reservations/box-office-data")
    BoxOfficeDataDTO getBoxOfficeData(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "movieId", required = false) String movieId
    );
}
