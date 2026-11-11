package com.cinasee.componente2_reservas;

import com.cinasee.componente2_reservas.clients.CarteleraClient;
import com.cinasee.componente2_reservas.clients.UsuariosClient;
import com.cinasee.componente2_reservas.dto.PaymentRequest;
import com.cinasee.componente2_reservas.dto.RedsysPaymentForm;
import com.cinasee.componente2_reservas.dto.RedsysResultRequest;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.events.EventPublisher;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.models.OutboxEvent;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.repositories.OutboxEventRepository;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.PaymentService;
import com.cinasee.componente2_reservas.services.RedsysPaymentService;
import com.cinasee.componente2_reservas.services.ReservationService;
import com.cinasee.componente2_reservas.services.SeatLockService;
import com.cinasee.componente2_reservas.services.payment.RedsysSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Integración del pago con Redsys (app.payment.mode=redsys) sobre H2: transacciones,
 * bloqueos y outbox reales. La web de Redsys se sustituye por respuestas firmadas
 * fabricadas en el test con la clave inventada de los tests (sin internet).
 */
@SpringBootTest(properties = "app.payment.mode=redsys")
@ActiveProfiles("test")
class RedsysFlowIntegrationTest {

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
    @Autowired private RedsysPaymentService redsysPaymentService;
    @Autowired private PaymentService paymentService;
    @Autowired private SeatLockService seatLockService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private JsonMapper jsonMapper;

    private static final CallerIdentity GUEST_A = new CallerIdentity("guest:a", CallerIdentity.ROLE_GUEST);
    private static final CallerIdentity GUEST_B = new CallerIdentity("guest:b", CallerIdentity.ROLE_GUEST);

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

    private ReservationResponse reserve(CallerIdentity guest, int row, int column) {
        return reservationService.createReservation(
                TestData.request(null, guest.subject() + "@mail.com", TestData.seats(row, column)), guest);
    }

    /** Lo que haría Redsys: devolver al navegador una respuesta firmada para ese pedido. */
    private RedsysResultRequest redsysAnswers(RedsysPaymentForm form, String responseCode) {
        String amount = form.amount().movePointRight(2).toBigInteger().toString();
        String json = jsonMapper.writeValueAsString(Map.of("Ds_Order", form.order(), "Ds_Amount", amount,
                "Ds_Currency", "978", "Ds_MerchantCode", "999008881", "Ds_Response", responseCode));
        String params = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String signature = new RedsysSignature(TestData.TEST_REDSYS_KEY).sign(form.order(), params);
        return new RedsysResultRequest(RedsysSignature.VERSION, params, signature);
    }

    @Test
    void flujoCompleto_iniciarPagarEnRedsysYConfirmar() throws Exception {
        ReservationResponse reservation = reserve(GUEST_A, 8, 12);

        RedsysPaymentForm form = redsysPaymentService.startPayment(reservation.id(), GUEST_A);
        clock.advance(Duration.ofMinutes(2)); // el cliente paga en la web de Redsys
        var confirmation = redsysPaymentService.confirmPayment(reservation.id(), redsysAnswers(form, "0000"), GUEST_A);

        assertThat(confirmation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmation.tickets()).singleElement().asString().startsWith("QR-" + reservation.id() + "-8-12-");
        var stored = reservationRepository.findById(reservation.id()).orElseThrow();
        assertThat(stored.getPaymentGateway()).isEqualTo("REDSYS");
        assertThat(stored.getPaymentReference()).isEqualTo(form.order());

        // Mismo evento que con la pasarela simulada: los Componentes 3 y 4 no notan la diferencia
        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertThat(outbox).singleElement().extracting(OutboxEvent::getRoutingKey).isEqualTo("payment.completed");
        assertThat(jsonMapper.readTree(outbox.getFirst().getPayload()).get("amount").decimalValue())
                .isEqualByComparingTo("8.50");
    }

    @Test
    void mientrasSePagaEnRedsys_lasButacasNoCaducanNiLasCogeOtro() {
        ReservationResponse reservation = reserve(GUEST_A, 5, 5);
        clock.advance(Duration.ofMinutes(9));
        RedsysPaymentForm form = redsysPaymentService.startPayment(reservation.id(), GUEST_A);

        // Minuto 12: ya pasaron los 10 minutos, pero el cliente sigue en Redsys
        clock.advance(Duration.ofMinutes(3));
        assertThat(seatLockService.expireOverdueReservations()).isZero();
        assertThatThrownBy(() -> reserve(GUEST_B, 5, 5)).isInstanceOf(SeatsUnavailableException.class);
        assertThat(reservationService.getSeatMap(TestData.SHOW_ID).occupiedSeats()).hasSize(1);

        // Minuto 14: vuelve de Redsys con el pago autorizado → se confirma
        clock.advance(Duration.ofMinutes(2));
        redsysPaymentService.confirmPayment(reservation.id(), redsysAnswers(form, "0000"), GUEST_A);
        assertThat(reservationRepository.findById(reservation.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pagoAbandonadoEnRedsys_caducaAlAcabarElMargen() {
        ReservationResponse reservation = reserve(GUEST_A, 6, 6);
        clock.advance(Duration.ofMinutes(9));
        redsysPaymentService.startPayment(reservation.id(), GUEST_A);

        // El margen es de 15 min desde el inicio del pago (minuto 9 + 15 = 24)
        clock.advance(Duration.ofMinutes(16));
        assertThat(seatLockService.expireOverdueReservations()).isEqualTo(1);
        assertThat(reservationRepository.findById(reservation.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        // La butaca vuelve a estar libre
        assertThat(reserve(GUEST_B, 6, 6).status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void pagoDenegado_seguidoDeReintentoConOtroPedido() {
        ReservationResponse reservation = reserve(GUEST_A, 7, 7);
        RedsysPaymentForm first = redsysPaymentService.startPayment(reservation.id(), GUEST_A);

        assertThatThrownBy(() -> redsysPaymentService.confirmPayment(reservation.id(), redsysAnswers(first, "0190"), GUEST_A))
                .isInstanceOf(PaymentDeclinedException.class);
        // La denegación se guarda: el pago ya no está "en curso"
        assertThat(reservationRepository.findById(reservation.id()).orElseThrow().getPaymentStartedAt()).isNull();

        clock.advance(Duration.ofSeconds(30));
        RedsysPaymentForm second = redsysPaymentService.startPayment(reservation.id(), GUEST_A);
        assertThat(second.order()).isNotEqualTo(first.order()); // Redsys rechaza pedidos repetidos
        redsysPaymentService.confirmPayment(reservation.id(), redsysAnswers(second, "0000"), GUEST_A);

        assertThat(reservationRepository.findById(reservation.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void conRedsysActivo_elPagoDirectoIndicaElFlujoCorrecto() {
        ReservationResponse reservation = reserve(GUEST_A, 9, 9);

        assertThatThrownBy(() -> paymentService.payReservation(reservation.id(),
                new PaymentRequest(PaymentRequest.PaymentGatewayType.REDSYS, "tok_visa"), GUEST_A))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("/payment/redsys");
    }
}
