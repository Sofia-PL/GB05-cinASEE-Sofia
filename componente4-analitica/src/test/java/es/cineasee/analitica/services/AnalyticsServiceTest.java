package es.cineasee.analitica.services;

import es.cineasee.analitica.clients.CarteleraClient;
import es.cineasee.analitica.clients.ReservasClient;
import es.cineasee.analitica.clients.UsuariosClient;
import es.cineasee.analitica.clients.dto.BoxOfficeDataDTO;
import es.cineasee.analitica.clients.dto.BoxOfficeDataDTO.MovieBoxOfficeDTO;
import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.clients.dto.PurchaseHistoryItemDTO;
import es.cineasee.analitica.clients.dto.RoomDTO;
import es.cineasee.analitica.clients.dto.ShowStatsDTO;
import es.cineasee.analitica.clients.dto.UserProfileDTO;
import es.cineasee.analitica.dto.BoxOfficeReportDTO;
import es.cineasee.analitica.dto.OccupancyMetricDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO;
import es.cineasee.analitica.dto.RecommendationResponseDTO.RecommendationItemDTO;
import es.cineasee.analitica.repositories.AnalyticsEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de la lógica de analítica: ocupación, taquilla y recomendaciones.
 * Los clientes Feign (C1, C2, C3) y el repositorio son mocks.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String CINEMA = "cinema-1";

    /** Viernes 16/10/2026 a las 18:30 en Madrid (UTC+2). */
    private static final Instant FRIDAY_1830 = Instant.parse("2026-10-16T16:30:00Z");
    /** Viernes 16/10/2026 a las 22:00 en Madrid. */
    private static final Instant FRIDAY_2200 = Instant.parse("2026-10-16T20:00:00Z");
    /** Jueves 15/10/2026 a las 18:30 en Madrid. */
    private static final Instant THURSDAY_1830 = Instant.parse("2026-10-15T16:30:00Z");
    /** 22:30 UTC del viernes = 00:30 del SÁBADO en Madrid. */
    private static final Instant SATURDAY_0030_MADRID = Instant.parse("2026-10-16T22:30:00Z");

    @Mock
    private AnalyticsEventRepository repository;
    @Mock
    private CarteleraClient carteleraClient;
    @Mock
    private UsuariosClient usuariosClient;
    @Mock
    private ReservasClient reservasClient;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(repository, carteleraClient, usuariosClient, reservasClient, "Europe/Madrid");
    }

    private static RoomDTO room(String id, String cinemaId, Integer capacity) {
        return RoomDTO.builder().id(id).cinemaId(cinemaId).capacity(capacity).build();
    }

    private static ShowStatsDTO stats(String roomId, Instant start, long tickets) {
        return new ShowStatsDTO("show-" + roomId + "-" + start, "movie-1", roomId, start, tickets, 1,
                new BigDecimal("9.25").multiply(BigDecimal.valueOf(tickets)), "EUR");
    }

    // ------------------------------------------------------------------ ocupación

    @Nested
    class Occupancy {

        @Test
        void singleRoomCrossesCapacityFromCarteleraWithSalesFromReservas() {
            when(carteleraClient.getRoomsByCinema(CINEMA))
                    .thenReturn(List.of(room("r1", CINEMA, 100), room("r2", CINEMA, 50)));
            when(reservasClient.getShowStats(null, "r1", null, null))
                    .thenReturn(List.of(stats("r1", FRIDAY_1830, 30), stats("r2", FRIDAY_1830, 10)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, "r1", null, null, null);

            assertThat(metric.getCinemaId()).isEqualTo(CINEMA);
            assertThat(metric.getScreenId()).isEqualTo("r1");
            assertThat(metric.getTotalCapacity()).isEqualTo(100);
            assertThat(metric.getTotalTicketsSold()).isEqualTo(30);
            assertThat(metric.getOccupancyPercentage()).isEqualTo(30.0);
            assertThat(metric.getTimeRange()).isNull();
            verify(carteleraClient, never()).getRoomById(any());
        }

        @Test
        void wholeCinemaSumsItsRoomsAndIgnoresSalesFromOtherCinemas() {
            when(carteleraClient.getRoomsByCinema(CINEMA))
                    .thenReturn(List.of(room("r1", CINEMA, 100), room("r2", CINEMA, 50)));
            when(reservasClient.getShowStats(null, null, null, null)).thenReturn(List.of(
                    stats("r1", FRIDAY_1830, 30),
                    stats("r2", FRIDAY_1830, 10),
                    stats("otra-sala", FRIDAY_1830, 99)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, null, null, null, null);

            assertThat(metric.getTotalCapacity()).isEqualTo(150);
            assertThat(metric.getTotalTicketsSold()).isEqualTo(40);
            assertThat(metric.getOccupancyPercentage()).isEqualTo(26.67);
        }

        @Test
        void cinemaWithoutRoomsHasZeroCapacityAndZeroSales() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of());
            when(reservasClient.getShowStats(null, null, null, null))
                    .thenReturn(List.of(stats("r1", FRIDAY_1830, 30)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, null, null, null, null);

            assertThat(metric.getTotalCapacity()).isZero();
            assertThat(metric.getTotalTicketsSold()).isZero();
            assertThat(metric.getOccupancyPercentage()).isZero();
        }

        @Test
        void roomWithoutCapacityCountsAsOneHundredSeatsInCinemaTotal() {
            when(carteleraClient.getRoomsByCinema(CINEMA))
                    .thenReturn(List.of(room("r1", CINEMA, null), room("r2", CINEMA, 50)));
            when(reservasClient.getShowStats(null, null, null, null)).thenReturn(List.of());

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, null, null, null, null);

            assertThat(metric.getTotalCapacity()).isEqualTo(150);
            assertThat(metric.getTotalTicketsSold()).isZero();
        }

        @Test
        void dayOfWeekFilterUsesConfiguredTimeZone() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 100)));
            when(reservasClient.getShowStats(null, null, null, null)).thenReturn(List.of(
                    stats("r1", FRIDAY_1830, 10),
                    stats("r1", THURSDAY_1830, 20),
                    // En UTC es viernes, pero en Madrid ya es sábado: no cuenta como viernes
                    stats("r1", SATURDAY_0030_MADRID, 40)));

            OccupancyMetricDTO friday = service.calculateOccupancy(CINEMA, null, 5, null, null);
            OccupancyMetricDTO saturday = service.calculateOccupancy(CINEMA, null, 6, null, null);

            assertThat(friday.getTotalTicketsSold()).isEqualTo(10);
            assertThat(friday.getDayOfWeek()).isEqualTo(5);
            assertThat(saturday.getTotalTicketsSold()).isEqualTo(40);
        }

        @Test
        void timeRangeFilterKeepsOnlySessionsInsideTheSlot() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 100)));
            when(reservasClient.getShowStats(null, null, null, null)).thenReturn(List.of(
                    stats("r1", FRIDAY_1830, 10),
                    stats("r1", FRIDAY_2200, 25)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, null, null, "18:00", "20:00");

            assertThat(metric.getTotalTicketsSold()).isEqualTo(10);
            assertThat(metric.getTimeRange()).isEqualTo("18:00-20:00");
        }

        @Test
        void invalidTimeRangeIsIgnored() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 100)));
            when(reservasClient.getShowStats(null, null, null, null)).thenReturn(List.of(
                    stats("r1", FRIDAY_1830, 10),
                    stats("r1", FRIDAY_2200, 25)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, null, null, "no-es-hora", null);

            assertThat(metric.getTotalTicketsSold()).isEqualTo(35);
        }

        @Test
        void occupancyIsCappedAtOneHundredPercent() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 10)));
            when(reservasClient.getShowStats(null, "r1", null, null))
                    .thenReturn(List.of(stats("r1", FRIDAY_1830, 15)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, "r1", null, null, null);

            assertThat(metric.getOccupancyPercentage()).isEqualTo(100.0);
        }

        @Test
        void roomFromAnotherCinemaHasZeroCapacity() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 100)));
            when(carteleraClient.getRoomById("r9")).thenReturn(room("r9", "cinema-2", 80));
            when(reservasClient.getShowStats(null, "r9", null, null))
                    .thenReturn(List.of(stats("r9", FRIDAY_1830, 20)));

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, "r9", null, null, null);

            assertThat(metric.getTotalCapacity()).isZero();
            assertThat(metric.getTotalTicketsSold()).isZero();
            assertThat(metric.getOccupancyPercentage()).isZero();
        }

        @Test
        void reservasDownFallsBackToLocalEvents() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenReturn(List.of(room("r1", CINEMA, 100)));
            when(reservasClient.getShowStats(null, "r1", null, null)).thenThrow(new RuntimeException("C2 caído"));
            when(repository.sumTicketsSold(CINEMA, "r1", 5)).thenReturn(42L);

            OccupancyMetricDTO metric = service.calculateOccupancy(CINEMA, "r1", 5, null, null);

            assertThat(metric.getTotalTicketsSold()).isEqualTo(42);
            assertThat(metric.getOccupancyPercentage()).isEqualTo(42.0);
        }

        @Test
        void carteleraDownUsesEstimatedCapacities() {
            when(carteleraClient.getRoomsByCinema(CINEMA)).thenThrow(new RuntimeException("C1 caído"));
            when(carteleraClient.getRoomById("r1")).thenThrow(new RuntimeException("C1 caído"));
            when(reservasClient.getShowStats(any(), any(), any(), any())).thenReturn(List.of());

            OccupancyMetricDTO cinema = service.calculateOccupancy(CINEMA, null, null, null, null);
            OccupancyMetricDTO screen = service.calculateOccupancy(CINEMA, "r1", null, null, null);

            assertThat(cinema.getTotalCapacity()).isEqualTo(500);
            assertThat(screen.getTotalCapacity()).isEqualTo(150);
        }

        @Test
        void withoutCinemaAllRoomsAreUsed() {
            when(carteleraClient.getAllRooms()).thenReturn(List.of(room("r1", "a", 100), room("r2", "b", 100)));
            when(reservasClient.getShowStats(null, null, null, null))
                    .thenReturn(List.of(stats("r1", FRIDAY_1830, 50), stats("r2", FRIDAY_1830, 50)));

            OccupancyMetricDTO metric = service.calculateOccupancy(null, null, null, null, null);

            assertThat(metric.getTotalCapacity()).isEqualTo(200);
            assertThat(metric.getTotalTicketsSold()).isEqualTo(100);
            assertThat(metric.getOccupancyPercentage()).isEqualTo(50.0);
        }
    }

    // ------------------------------------------------------------------ taquilla

    @Nested
    class BoxOffice {

        private final LocalDate start = LocalDate.of(2026, 10, 1);
        private final LocalDate end = LocalDate.of(2026, 10, 31);

        @Test
        void startDateAfterEndDateIsBadRequest() {
            assertThatThrownBy(() -> service.generateBoxOfficeReport(null, null, end, start))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void generalReportAddsUpAllMoviesFromReservas() {
            when(reservasClient.getBoxOfficeData(start, end, null)).thenReturn(new BoxOfficeDataDTO(start, end, "EUR",
                    List.of(new MovieBoxOfficeDTO("m1", 10, 4, new BigDecimal("92.50")),
                            new MovieBoxOfficeDTO("m2", 5, 2, new BigDecimal("46.25")))));

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(null, null, start, end);

            assertThat(report.getMovieTitle()).isEqualTo("Resumen General Box Office");
            assertThat(report.getTotalTicketsSold()).isEqualTo(15L);
            assertThat(report.getGrossRevenue()).isEqualTo(138.75);
            assertThat(report.getCurrency()).isEqualTo("EUR");
            assertThat(report.getPeriod().getStartDate()).isEqualTo(start);
            assertThat(report.getPeriod().getEndDate()).isEqualTo(end);
        }

        @Test
        void movieReportResolvesTitleFromCartelera() {
            when(reservasClient.getBoxOfficeData(start, end, "m1")).thenReturn(new BoxOfficeDataDTO(start, end, "EUR",
                    List.of(new MovieBoxOfficeDTO("m1", 10, 4, new BigDecimal("92.50")))));
            when(carteleraClient.getMovieById("m1")).thenReturn(MovieDTO.builder().id("m1").title("Dune").build());

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(null, "m1", start, end);

            assertThat(report.getMovieId()).isEqualTo("m1");
            assertThat(report.getMovieTitle()).isEqualTo("Dune");
            assertThat(report.getTotalTicketsSold()).isEqualTo(10L);
        }

        @Test
        void unknownTitleUsesPlaceholderWhenCarteleraFails() {
            when(reservasClient.getBoxOfficeData(start, end, "m1")).thenReturn(new BoxOfficeDataDTO(start, end, "EUR", List.of()));
            when(carteleraClient.getMovieById("m1")).thenThrow(new RuntimeException("C1 caído"));

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(null, "m1", start, end);

            assertThat(report.getMovieTitle()).isEqualTo("Película #m1");
            assertThat(report.getTotalTicketsSold()).isZero();
            assertThat(report.getGrossRevenue()).isZero();
        }

        @Test
        void emptyResponseFromReservasGivesZeroReportInEuros() {
            when(reservasClient.getBoxOfficeData(start, end, null)).thenReturn(null);

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(null, null, start, end);

            assertThat(report.getTotalTicketsSold()).isZero();
            assertThat(report.getGrossRevenue()).isZero();
            assertThat(report.getCurrency()).isEqualTo("EUR");
        }

        @Test
        void reservasDownFallsBackToLocalEvents() {
            when(reservasClient.getBoxOfficeData(start, end, "m1")).thenThrow(new RuntimeException("C2 caído"));
            when(repository.findBoxOfficeAggregate(start.atStartOfDay(), end.atTime(LocalTime.MAX), "m1", 3L))
                    .thenReturn(Collections.singletonList(new Object[]{"m1", "Dune", "Warner", 7L, 63.5}));

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(3L, "m1", start, end);

            assertThat(report.getDistributorId()).isEqualTo(3L);
            assertThat(report.getMovieTitle()).isEqualTo("Dune");
            assertThat(report.getTotalTicketsSold()).isEqualTo(7L);
            assertThat(report.getGrossRevenue()).isEqualTo(63.5);
            assertThat(report.getCurrency()).isEqualTo("EUR");
            verify(carteleraClient, never()).getMovieById(any());
        }

        @Test
        void missingDatesDefaultToLastThirtyDays() {
            LocalDate today = LocalDate.now();
            when(reservasClient.getBoxOfficeData(any(), any(), isNull()))
                    .thenReturn(new BoxOfficeDataDTO(null, null, null, List.of()));

            BoxOfficeReportDTO report = service.generateBoxOfficeReport(null, null, null, null);

            verify(reservasClient).getBoxOfficeData(eq(today.minusDays(30)), eq(today), isNull());
            assertThat(report.getPeriod().getStartDate()).isEqualTo(today.minusDays(30));
            assertThat(report.getPeriod().getEndDate()).isEqualTo(today);
        }
    }

    // ------------------------------------------------------------------ recomendaciones

    @Nested
    class Recommendations {

        private static final Long USER = 5590L;

        private MovieDTO movie(String id, String title, String genre) {
            return MovieDTO.builder().id(id).title(title).genre(genre).build();
        }

        private void givenProfile(String... preferredGenres) {
            when(usuariosClient.getUserProfile(USER)).thenReturn(UserProfileDTO.builder()
                    .id(USER).preferredGenres(List.of(preferredGenres)).build());
        }

        @Test
        void unknownUserIsNotFound() {
            when(usuariosClient.getUserProfile(USER)).thenThrow(new RuntimeException("404"));

            assertThatThrownBy(() -> service.getRecommendationsForUser(USER, 5))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void moviesAreScoredByPreferenceHistoryAndAlreadyWatched() {
            givenProfile("sci-fi");
            when(usuariosClient.getUserHistory(USER)).thenReturn(List.of(
                    PurchaseHistoryItemDTO.builder().reservationId(1L).movieId("m4").build()));
            when(carteleraClient.getMovies(null, true)).thenReturn(List.of(
                    movie("m3", "Comedia", "COMEDY"),
                    movie("m2", "Drama", "DRAMA"),
                    movie("m1", "Dune", "SCI-FI"),
                    movie("m4", "Interstellar", "SCI-FI")));
            when(repository.findTopGenresByUserId(USER))
                    .thenReturn(Collections.singletonList(new Object[]{"drama", 2L, 3L}));

            RecommendationResponseDTO response = service.getRecommendationsForUser(USER, 10);

            assertThat(response.getUserId()).isEqualTo(USER);
            assertThat(response.getRecommendations())
                    .extracting(RecommendationItemDTO::getMovieId)
                    .containsExactly("m1", "m2", "m3", "m4");
            assertThat(response.getRecommendations())
                    .extracting(RecommendationItemDTO::getMatchScore)
                    .containsExactly(0.95, 0.85, 0.5, 0.35);
            assertThat(response.getRecommendations().get(0).getReason()).isEqualTo("Basado en tu preferencia por SCI-FI");
            assertThat(response.getRecommendations().get(1).getReason()).isEqualTo("Similar a películas que has visto recientemente");
            assertThat(response.getRecommendations().get(2).getReason()).isEqualTo("Película destacada en cartelera");
        }

        @Test
        void scoreNeverDropsBelowMinimum() {
            givenProfile();
            when(usuariosClient.getUserHistory(USER)).thenReturn(List.of(
                    PurchaseHistoryItemDTO.builder().movieId("m1").build()));
            when(carteleraClient.getMovies(null, true)).thenReturn(List.of(movie("m1", "Comedia", "COMEDY")));

            RecommendationResponseDTO response = service.getRecommendationsForUser(USER, 5);

            assertThat(response.getRecommendations().get(0).getMatchScore()).isEqualTo(0.1);
        }

        @Test
        void limitIsAppliedAndDefaultsToFive() {
            givenProfile();
            when(usuariosClient.getUserHistory(USER)).thenReturn(List.of());
            List<MovieDTO> catalog = List.of(movie("a", "A", "X"), movie("b", "B", "X"), movie("c", "C", "X"),
                    movie("d", "D", "X"), movie("e", "E", "X"), movie("f", "F", "X"), movie("g", "G", "X"));
            when(carteleraClient.getMovies(null, true)).thenReturn(catalog);

            assertThat(service.getRecommendationsForUser(USER, 2).getRecommendations()).hasSize(2);
            assertThat(service.getRecommendationsForUser(USER, null).getRecommendations()).hasSize(5);
            assertThat(service.getRecommendationsForUser(USER, 0).getRecommendations()).hasSize(5);
        }

        @Test
        void emptyShowcaseFallsBackToFullCatalog() {
            givenProfile();
            when(usuariosClient.getUserHistory(USER)).thenReturn(List.of());
            when(carteleraClient.getMovies(null, true)).thenReturn(List.of());
            when(carteleraClient.getMovies(null, false)).thenReturn(List.of(movie("m1", "Dune", "SCI-FI")));

            RecommendationResponseDTO response = service.getRecommendationsForUser(USER, 5);

            assertThat(response.getRecommendations()).extracting(RecommendationItemDTO::getMovieId).containsExactly("m1");
        }

        @Test
        void failuresInHistoryOrCatalogDoNotBreakTheResponse() {
            givenProfile("sci-fi");
            when(usuariosClient.getUserHistory(USER)).thenThrow(new RuntimeException("C3 caído"));
            when(carteleraClient.getMovies(null, true)).thenThrow(new RuntimeException("C1 caído"));

            RecommendationResponseDTO response = service.getRecommendationsForUser(USER, 5);

            assertThat(response.getUserId()).isEqualTo(USER);
            assertThat(response.getRecommendations()).isEmpty();
        }

        @Test
        void moviesWithoutIdAreSkipped() {
            givenProfile();
            when(usuariosClient.getUserHistory(USER)).thenReturn(null);
            when(carteleraClient.getMovies(null, true))
                    .thenReturn(List.of(movie(null, "Sin id", "X"), movie("m1", "Dune", "SCI-FI")));

            RecommendationResponseDTO response = service.getRecommendationsForUser(USER, 5);

            assertThat(response.getRecommendations()).extracting(RecommendationItemDTO::getMovieId).containsExactly("m1");
        }
    }
}
