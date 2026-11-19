package es.cineasee.analitica.repositories;

import es.cineasee.analitica.models.AnalyticsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de las consultas JPQL del repositorio sobre H2 en memoria.
 */
@DataJpaTest
@ActiveProfiles("test")
class AnalyticsEventRepositoryTest {

    @Autowired
    private AnalyticsEventRepository repository;

    private static AnalyticsEvent event(long reservationId, Long userId, String cinemaId, String roomId,
                                        String movieId, String title, String genre, Long distributorId,
                                        int tickets, double amount, int dayOfWeek, LocalDateTime timestamp) {
        return AnalyticsEvent.builder()
                .reservationId(reservationId).userId(userId).cinemaId(cinemaId).roomId(roomId)
                .movieId(movieId).movieTitle(title).genre(genre).distributor("Distribuidora")
                .distributorId(distributorId).ticketsSold(tickets).totalAmount(amount)
                .dayOfWeek(dayOfWeek).sessionTime("18:30").eventType("PAYMENT_COMPLETED")
                .eventTimestamp(timestamp).build();
    }

    @BeforeEach
    void setUp() {
        repository.saveAll(List.of(
                // cine c1, sala r1, viernes (5)
                event(1, 10L, "c1", "r1", "m1", "Dune", "SCI-FI", 3L, 2, 18.0, 5,
                        LocalDateTime.of(2026, 10, 16, 18, 30)),
                // cine c1, sala r2, viernes
                event(2, 10L, "c1", "r2", "m1", "Dune", "SCI-FI", 3L, 3, 27.0, 5,
                        LocalDateTime.of(2026, 10, 16, 21, 0)),
                // cine c1, sala r1, sábado (6)
                event(3, 20L, "c1", "r1", "m2", "Drama", "DRAMA", 4L, 4, 36.0, 6,
                        LocalDateTime.of(2026, 10, 17, 18, 30)),
                // cine c2: no debe mezclarse con c1
                event(4, 10L, "c2", "r9", "m2", "Drama", "DRAMA", 4L, 10, 90.0, 5,
                        LocalDateTime.of(2026, 10, 16, 18, 30)),
                // usuario 10, fuera del periodo de octubre
                event(5, 10L, "c1", "r1", "m3", "Comedia", "COMEDY", 5L, 1, 9.0, 3,
                        LocalDateTime.of(2026, 11, 4, 18, 30))
        ));
    }

    @Test
    void sumTicketsSoldFiltersByCinemaRoomAndDay() {
        assertThat(repository.sumTicketsSold("c1", null, null)).isEqualTo(10L);
        assertThat(repository.sumTicketsSold("c1", "r1", null)).isEqualTo(7L);
        assertThat(repository.sumTicketsSold("c1", null, 5)).isEqualTo(5L);
        assertThat(repository.sumTicketsSold("c1", "r1", 6)).isEqualTo(4L);
    }

    @Test
    void sumTicketsSoldWithoutMatchesIsZero() {
        assertThat(repository.sumTicketsSold("no-existe", null, null)).isZero();
    }

    @Test
    void avgTicketsSoldPerEventByCinema() {
        assertThat(repository.avgTicketsSoldPerEvent("c1", "r1")).isEqualTo(7.0 / 3);
        assertThat(repository.avgTicketsSoldPerEvent("no-existe", null)).isZero();
    }

    @Test
    void boxOfficeAggregateGroupsByMovieWithinPeriod() {
        List<Object[]> rows = repository.findBoxOfficeAggregate(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59), null, null);

        assertThat(rows).hasSize(2);
        Object[] dune = rows.stream().filter(r -> "m1".equals(r[0])).findFirst().orElseThrow();
        Object[] drama = rows.stream().filter(r -> "m2".equals(r[0])).findFirst().orElseThrow();
        assertThat(dune[1]).isEqualTo("Dune");
        assertThat(((Number) dune[3]).longValue()).isEqualTo(5L);
        assertThat(((Number) dune[4]).doubleValue()).isEqualTo(45.0);
        assertThat(((Number) drama[3]).longValue()).isEqualTo(14L);
        assertThat(((Number) drama[4]).doubleValue()).isEqualTo(126.0);
    }

    @Test
    void boxOfficeAggregateFiltersByMovieAndDistributor() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 31, 23, 59);

        List<Object[]> byMovie = repository.findBoxOfficeAggregate(start, end, "m3", null);
        List<Object[]> byDistributor = repository.findBoxOfficeAggregate(start, end, null, 4L);

        assertThat(byMovie).hasSize(1);
        assertThat(byMovie.get(0)[0]).isEqualTo("m3");
        assertThat(byDistributor).hasSize(1);
        assertThat(byDistributor.get(0)[0]).isEqualTo("m2");
    }

    @Test
    void roomAggregationsByCinema() {
        List<Object[]> rows = repository.findRoomAggregationsByCinema("c1");

        assertThat(rows).hasSize(2);
        Object[] r1 = rows.stream().filter(r -> "r1".equals(r[0])).findFirst().orElseThrow();
        assertThat(((Number) r1[1]).longValue()).isEqualTo(7L);
    }

    @Test
    void topGenresByUserAreOrderedByTicketsSold() {
        List<Object[]> rows = repository.findTopGenresByUserId(10L);

        assertThat(rows).extracting(r -> r[0]).containsExactly("DRAMA", "SCI-FI", "COMEDY");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(10L);
    }

    @Test
    void findByUserId() {
        assertThat(repository.findByUserId(20L)).extracting(AnalyticsEvent::getReservationId).containsExactly(3L);
        assertThat(repository.findByUserId(99L)).isEmpty();
    }
}
