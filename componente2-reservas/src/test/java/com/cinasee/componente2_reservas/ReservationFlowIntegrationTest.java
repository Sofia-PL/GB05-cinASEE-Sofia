package com.cinasee.componente2_reservas;

import com.cinasee.componente2_reservas.clients.CarteleraClient;
import com.cinasee.componente2_reservas.clients.UsuariosClient;
import com.cinasee.componente2_reservas.dto.PaymentRequest;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.events.EventPublisher;
import com.cinasee.componente2_reservas.events.OutboxRelay;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.models.OutboxEvent;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.repositories.OutboxEventRepository;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.CancellationService;
import com.cinasee.componente2_reservas.services.PaymentService;
import com.cinasee.componente2_reservas.services.ReportService;
import com.cinasee.componente2_reservas.services.ReservationService;
import com.cinasee.componente2_reservas.services.SeatLockService;
import com.cinasee.componente2_reservas.services.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test de integración del microservicio completo (Spring + JPA + transacciones)
 * sobre H2 en memoria en modo PostgreSQL.
 *
 * Solo se sustituyen por mocks las fronteras del sistema: los clientes Feign de
 * Cartelera/Usuarios y el publicador de RabbitMQ. Todo lo demás es real: la
 * restricción UNIQUE de butacas, los bloqueos FOR UPDATE, el TTL, el outbox...
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationFlowIntegrationTest {

    /** Reloj controlable que sustituye al reloj del sistema. */
    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(TestData.NOW);
        }
    }

    @MockitoBean
    private CarteleraClient carteleraClient;
    @MockitoBean
    private UsuariosClient usuariosClient;
    @MockitoBean
    private EventPublisher eventPublisher;

    @Autowired private MutableClock clock;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentService paymentService;
    @Autowired private CancellationService cancellationService;
    @Autowired private TicketService ticketService;
    @Autowired private ReportService reportService;
    @Autowired private SeatLockService seatLockService;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private JsonMapper jsonMapper;

    private static final PaymentRequest PAY_OK = new PaymentRequest(PaymentRequest.PaymentGatewayType.REDSYS, "tok_visa_ok");

    @BeforeEach
    void setUp() {
        reservedSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        outboxEventRepository.deleteAll();
        clock.set(TestData.NOW);

        when(carteleraClient.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(carteleraClient.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        when(usuariosClient.getUserProfile(anyLong())).thenReturn(TestData.user(0));
    }

    private ReservationResponse reserveAsGuest(String email, int row, int column) {
        CallerIdentity guest = new CallerIdentity("guest:" + email, CallerIdentity.ROLE_GUEST);
        return reservationService.createReservation(TestData.request(null, email, TestData.seats(row, column)), guest);
    }

    @Test
    void dobleVentaImposible_concurrenciaReal() throws Exception {
        int users = 8;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResponse>> results = new ArrayList<>();

        // 8 invitados intentan bloquear LA MISMA butaca en el mismo instante
        for (int i = 0; i < users; i++) {
            String email = "user" + i + "@mail.com";
            Callable<ReservationResponse> task = () -> {
                start.await();
                return reserveAsGuest(email, 5, 5);
            };
            results.add(pool.submit(task));
        }
        start.countDown();

        int ok = 0;
        int conflicts = 0;
        for (Future<ReservationResponse> result : results) {
            try {
                result.get();
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(SeatsUnavailableException.class);
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflicts).isEqualTo(users - 1);
        assertThat(reservedSeatRepository.findByShowIdWithReservation(TestData.SHOW_ID)).hasSize(1);
    }

    @Test
    void butacaBloqueada_noPuedeReservarlaOtro_hastaQueCaduqueElTtl() {
        ReservationResponse first = reserveAsGuest("a@mail.com", 8, 12);
        assertThat(first.expiresAt()).isEqualTo(TestData.NOW.plus(Duration.ofMinutes(10)));

        // A los 9 minutos sigue bloqueada
        clock.advance(Duration.ofMinutes(9));
        assertThatThrownBy(() -> reserveAsGuest("b@mail.com", 8, 12))
                .isInstanceOf(SeatsUnavailableException.class)
                .hasMessageContaining("8-12");

        // A los 10 minutos el bloqueo caduca y otra persona puede reservarla
        clock.advance(Duration.ofMinutes(1));
        ReservationResponse second = reserveAsGuest("b@mail.com", 8, 12);

        assertThat(second.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationRepository.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void barridoPeriodico_liberaLasButacasCaducadas() {
        reserveAsGuest("a@mail.com", 1, 1);
        reserveAsGuest("b@mail.com", 1, 2);
        assertThat(reservationService.getSeatMap(TestData.SHOW_ID).occupiedSeats()).hasSize(2);

        clock.advance(Duration.ofMinutes(11));
        int expired = seatLockService.expireOverdueReservations();

        assertThat(expired).isEqualTo(2);
        assertThat(reservedSeatRepository.count()).isZero();
        assertThat(reservationService.getSeatMap(TestData.SHOW_ID).occupiedSeats()).isEmpty();
    }

    @Test
    void flujoCompleto_reservarPagarYPublicarPaymentCompleted() throws Exception {
        CallerIdentity client = TestData.CLIENT;
        ReservationResponse reservation = reservationService.createReservation(
                TestData.request(TestData.USER_ID, null, TestData.seats(8, 12, 8, 13)), client);
        assertThat(reservation.totalAmount()).isEqualByComparingTo("17.00");

        clock.advance(Duration.ofMinutes(3));
        TicketConfirmation confirmation = paymentService.payReservation(reservation.id(), PAY_OK, client);

        assertThat(confirmation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmation.tickets()).hasSize(2).allMatch(code -> code.startsWith("QR-" + reservation.id() + "-8-"));

        // El evento está en el outbox, en la misma transacción que la confirmación
        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertThat(outbox).hasSize(1);
        OutboxEvent event = outbox.getFirst();
        assertThat(event.getRoutingKey()).isEqualTo("payment.completed");

        // El JSON cumple el contrato que consume el Componente 3
        JsonNode payload = jsonMapper.readTree(event.getPayload());
        assertThat(payload.get("reservationId").asLong()).isEqualTo(reservation.id());
        assertThat(payload.get("userId").asLong()).isEqualTo(TestData.USER_ID);
        assertThat(payload.get("movieId").asString()).isEqualTo(TestData.MOVIE_ID);
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("17.00");
        assertThat(payload.get("currency").asString()).isEqualTo("EUR");
        assertThat(payload.get("purchaseDate").asString()).isEqualTo("2026-10-08T12:03:00Z");
        assertThat(payload.get("eventId").asString()).isEqualTo(event.getId());

        // El relay lo publica y lo marca como enviado
        assertThat(outboxRelay.publishPendingEvents()).isEqualTo(1);
        verify(eventPublisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();

        // La butaca aparece como vendida en el mapa
        assertThat(reservationService.getSeatMap(TestData.SHOW_ID).occupiedSeats())
                .extracting(seat -> seat.status().name()).containsOnly("SOLD");
    }

    @Test
    void pagarTrasElTtl_400_yLaCaducidadQuedaGuardada() {
        ReservationResponse reservation = reserveAsGuest("a@mail.com", 2, 2);
        CallerIdentity guest = new CallerIdentity("guest:a@mail.com", CallerIdentity.ROLE_GUEST);

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThatThrownBy(() -> paymentService.payReservation(reservation.id(), PAY_OK, guest))
                .isInstanceOf(ReservationExpiredException.class);

        // noRollbackFor: aunque se lanza la excepción, EXPIRED y la liberación se confirman
        assertThat(reservationRepository.findById(reservation.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservedSeatRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void cancelarPendiente_liberaButacas_yDevolverPagada_publicaPaymentRefunded() {
        // Cancelación de una reserva pendiente
        ReservationResponse pending = reserveAsGuest("a@mail.com", 3, 3);
        CallerIdentity guest = new CallerIdentity("guest:a@mail.com", CallerIdentity.ROLE_GUEST);
        ReservationResponse cancelled = cancellationService.cancelReservation(pending.id(), guest);
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservedSeatRepository.count()).isZero();

        // Devolución de una reserva pagada
        ReservationResponse paid = reserveAsGuest("a@mail.com", 3, 3);
        paymentService.payReservation(paid.id(), PAY_OK, guest);
        ReservationResponse refunded = cancellationService.cancelReservation(paid.id(), guest);

        assertThat(refunded.status()).isEqualTo(ReservationStatus.REFUNDED);
        assertThat(refunded.seatCount()).isEqualTo(1);
        assertThat(reservedSeatRepository.count()).isZero();
        assertThat(outboxEventRepository.findAll()).extracting(OutboxEvent::getRoutingKey)
                .containsExactlyInAnyOrder("payment.completed", "payment.refunded");

        // Una reserva cerrada no se puede volver a cancelar
        assertThatThrownBy(() -> cancellationService.cancelReservation(paid.id(), guest))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void validacionEnPuerta_soloUnaVez() {
        ReservationResponse reservation = reserveAsGuest("a@mail.com", 4, 4);
        CallerIdentity guest = new CallerIdentity("guest:a@mail.com", CallerIdentity.ROLE_GUEST);
        String ticket = paymentService.payReservation(reservation.id(), PAY_OK, guest).tickets().getFirst();

        var checkIn = ticketService.checkIn(ticket);
        assertThat(checkIn.row()).isEqualTo(4);
        assertThat(checkIn.checkedInAt()).isEqualTo(TestData.NOW);

        assertThatThrownBy(() -> ticketService.checkIn(ticket))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("ya se validó");
        // Con una entrada usada tampoco se admite la devolución
        assertThatThrownBy(() -> cancellationService.cancelReservation(reservation.id(), guest))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void estadisticasYBoxOffice_soloCuentanReservasConfirmadas() {
        CallerIdentity guest = new CallerIdentity("guest:a@mail.com", CallerIdentity.ROLE_GUEST);
        ReservationResponse paid = reservationService.createReservation(
                TestData.request(null, "a@mail.com", TestData.seats(1, 1, 1, 2, 1, 3)), guest);
        paymentService.payReservation(paid.id(), PAY_OK, guest);
        reserveAsGuest("b@mail.com", 9, 9); // pendiente: no debe contar

        var stats = reportService.getShowStats(null, null, null, null);
        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().ticketsSold()).isEqualTo(3);
        assertThat(stats.getFirst().revenue()).isEqualByComparingTo("25.50");

        var boxOffice = reportService.getBoxOfficeData(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null);
        assertThat(boxOffice.movies()).hasSize(1);
        assertThat(boxOffice.movies().getFirst().movieId()).isEqualTo(TestData.MOVIE_ID);
        assertThat(boxOffice.movies().getFirst().totalTicketsSold()).isEqualTo(3);

        // Fuera del periodo no aparece
        assertThat(reportService.getBoxOfficeData(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), null)
                .movies()).isEmpty();
    }
}
