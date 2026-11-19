package es.cineasee.analitica.controllers;

import es.cineasee.analitica.config.SecurityConfig;
import es.cineasee.analitica.dto.BoxOfficeReportDTO;
import es.cineasee.analitica.dto.OccupancyMetricDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO.RecommendationItemDTO;
import es.cineasee.analitica.services.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la capa web: seguridad (JWT y roles), parámetros y respuesta JSON.
 * El servicio es un mock.
 */
@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;
    /** No se usa (jwt() inyecta el token ya validado) pero el Resource Server necesita el bean. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Simula un JWT del Componente 3 con el sub y el rol indicados. */
    private static JwtRequestPostProcessor token(String subject, String role) {
        return jwt().jwt(j -> j.subject(subject).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final JwtRequestPostProcessor STAFF = token("1", "STAFF_TAQUILLA");
    private static final JwtRequestPostProcessor ADMIN = token("2", "ADMIN");
    private static final JwtRequestPostProcessor CLIENT = token("5590", "CLIENTE_REGISTRADO");

    // ---------------------------------------------------------------- ocupación

    @Test
    void occupancyWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/metrics/occupancy").param("cinemaId", "cinema-1"))
                .andExpect(status().isUnauthorized());
        verify(analyticsService, never()).calculateOccupancy(any(), any(), any(), any(), any());
    }

    @Test
    void occupancyForClientIs403() throws Exception {
        mockMvc.perform(get("/metrics/occupancy").param("cinemaId", "cinema-1").with(CLIENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void occupancyForStaffReturnsMetric() throws Exception {
        when(analyticsService.calculateOccupancy("cinema-1", "r1", 5, "18:00", "20:00"))
                .thenReturn(OccupancyMetricDTO.builder().cinemaId("cinema-1").screenId("r1").dayOfWeek(5)
                        .timeRange("18:00-20:00").totalCapacity(100).totalTicketsSold(30)
                        .occupancyPercentage(30.0).build());

        mockMvc.perform(get("/metrics/occupancy").with(STAFF)
                        .param("cinemaId", "cinema-1").param("screenId", "r1").param("dayOfWeek", "5")
                        .param("startTime", "18:00").param("endTime", "20:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value("cinema-1"))
                .andExpect(jsonPath("$.timeRange").value("18:00-20:00"))
                .andExpect(jsonPath("$.totalCapacity").value(100))
                .andExpect(jsonPath("$.totalTicketsSold").value(30))
                .andExpect(jsonPath("$.occupancyPercentage").value(30.0));
    }

    @Test
    void occupancyWithoutCinemaIdIs400() throws Exception {
        mockMvc.perform(get("/metrics/occupancy").with(ADMIN))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- taquilla

    @Test
    void boxOfficeForClientIs403() throws Exception {
        mockMvc.perform(get("/reports/box-office").with(CLIENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void boxOfficeForAdminParsesDatesAndReturnsReport() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        when(analyticsService.generateBoxOfficeReport(3L, "m1", start, end))
                .thenReturn(BoxOfficeReportDTO.builder().distributorId(3L).movieId("m1").movieTitle("Dune")
                        .period(BoxOfficeReportDTO.PeriodDTO.builder().startDate(start).endDate(end).build())
                        .totalTicketsSold(15L).grossRevenue(138.75).currency("EUR").build());

        mockMvc.perform(get("/reports/box-office").with(ADMIN)
                        .param("distributorId", "3").param("movieId", "m1")
                        .param("startDate", "2026-10-01").param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Dune"))
                .andExpect(jsonPath("$.period.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.period.endDate").value("2026-10-31"))
                .andExpect(jsonPath("$.totalTicketsSold").value(15))
                .andExpect(jsonPath("$.grossRevenue").value(138.75))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void boxOfficeWithInvalidDateRangeIs400() throws Exception {
        when(analyticsService.generateBoxOfficeReport(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechas"));

        mockMvc.perform(get("/reports/box-office").with(STAFF)
                        .param("startDate", "2026-10-31").param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boxOfficeWithMalformedDateIs400() throws Exception {
        mockMvc.perform(get("/reports/box-office").with(STAFF).param("startDate", "31/10/2026"))
                .andExpect(status().isBadRequest());
        verify(analyticsService, never()).generateBoxOfficeReport(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- recomendaciones

    private static RecommendationResponseDTO sampleRecommendations() {
        return RecommendationResponseDTO.builder().userId(5590L).recommendations(List.of(
                RecommendationItemDTO.builder().movieId("m1").title("Dune").matchScore(0.95)
                        .reason("Basado en tu preferencia por SCI-FI").build())).build();
    }

    @Test
    void recommendationsWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/recommendations/users/5590"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanSeeOwnRecommendationsWithDefaultLimit() throws Exception {
        when(analyticsService.getRecommendationsForUser(5590L, 5)).thenReturn(sampleRecommendations());

        mockMvc.perform(get("/recommendations/users/5590").with(CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5590))
                .andExpect(jsonPath("$.recommendations[0].movieId").value("m1"))
                .andExpect(jsonPath("$.recommendations[0].matchScore").value(0.95));
        verify(analyticsService).getRecommendationsForUser(5590L, 5);
    }

    @Test
    void userCannotSeeOtherUsersRecommendations() throws Exception {
        mockMvc.perform(get("/recommendations/users/7777").with(CLIENT))
                .andExpect(status().isForbidden());
        verify(analyticsService, never()).getRecommendationsForUser(any(), any());
    }

    @Test
    void staffCanSeeAnyUsersRecommendationsWithCustomLimit() throws Exception {
        when(analyticsService.getRecommendationsForUser(5590L, 3)).thenReturn(sampleRecommendations());

        mockMvc.perform(get("/recommendations/users/5590").param("limit", "3").with(STAFF))
                .andExpect(status().isOk());
        verify(analyticsService).getRecommendationsForUser(5590L, 3);
    }

    @Test
    void unknownUserIs404() throws Exception {
        when(analyticsService.getRecommendationsForUser(9999L, 5))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe"));

        mockMvc.perform(get("/recommendations/users/9999").with(ADMIN))
                .andExpect(status().isNotFound());
    }
}
