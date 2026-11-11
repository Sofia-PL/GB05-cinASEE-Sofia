package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.config.RedsysProperties;
import com.cinasee.componente2_reservas.dto.RedsysPaymentForm;
import com.cinasee.componente2_reservas.dto.RedsysResultRequest;
import com.cinasee.componente2_reservas.events.OutboxService;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.InvalidPaymentResponseException;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.DiscountType;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.services.payment.RedsysSignature;
import com.cinasee.componente2_reservas.services.payment.SimulatedPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del pago con Redsys por redirección: formulario firmado de salida y
 * validación de la respuesta firmada de vuelta. Las respuestas de Redsys se fabrican
 * en el test con la clave inventada de TestData (sin internet).
 */
@ExtendWith(MockitoExtension.class)
class RedsysPaymentServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RedsysSignature REDSYS = new RedsysSignature(TestData.TEST_REDSYS_KEY);
    private static final String ORDER = "000089011234";

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatLockService seatLockService;
    @Mock
    private OutboxService outboxService;

    private RedsysPaymentService service;

    @BeforeEach
    void setUp() {
        service = buildService(TestData.redsysProperties(), "redsys");
    }

    private RedsysPaymentService buildService(RedsysProperties redsys, String mode) {
        PaymentService paymentService = new PaymentService(reservationRepository, seatLockService,
                new SimulatedPaymentGateway(), new TicketCodeGenerator(), outboxService, TestData.FIXED_CLOCK);
        return new RedsysPaymentService(reservationRepository, seatLockService, paymentService, redsys,
                TestData.reservationProperties(), JSON, TestData.FIXED_CLOCK, mode);
    }

    /** Reserva 8901 del cliente 5590: 2 butacas, 15,30 €, caduca dentro de 5 minutos. */
    private Reservation pendingReservation() {
        Reservation reservation = Reservation.builder()
                .id(8901L).showId(TestData.SHOW_ID).movieId(TestData.MOVIE_ID).roomId(TestData.ROOM_ID)
                .showStartTime(Instant.parse("2026-10-16T18:30:00Z"))
                .userId(TestData.USER_ID).ownerSubject("5590")
                .status(ReservationStatus.PENDING_PAYMENT)
                .unitPrice(new BigDecimal("7.65")).totalAmount(new BigDecimal("15.30")).currency("EUR")
                .discountType(DiscountType.LOYALTY).seatCount(2)
                .createdAt(TestData.NOW.minusSeconds(300)).expiresAt(TestData.NOW.plusSeconds(300))
                .build();
        reservation.addSeat(ReservedSeat.builder().showId(TestData.SHOW_ID).seatRow(8).seatColumn(12)
                .status(SeatStatus.HELD).price(new BigDecimal("7.65")).build());
        reservation.addSeat(ReservedSeat.builder().showId(TestData.SHOW_ID).seatRow(8).seatColumn(13)
                .status(SeatStatus.HELD).price(new BigDecimal("7.65")).build());
        return reservation;
    }

    /** Reserva con un pago ya iniciado con el pedido ORDER. */
    private Reservation paymentStarted() {
        Reservation reservation = pendingReservation();
        reservation.setPaymentOrder(ORDER);
        reservation.setPaymentStartedAt(TestData.NOW.minusSeconds(60));
        return reservation;
    }

    /** Fabrica la respuesta que Redsys añadiría a la URL OK/KO (Base64 URL-safe y firmada). */
    private static RedsysResultRequest redsysResponse(String order, String amount, String responseCode, String key) {
        String json = JSON.writeValueAsString(Map.of(
                "Ds_Order", order, "Ds_Amount", amount, "Ds_Currency", "978",
                "Ds_MerchantCode", "999008881", "Ds_Response", responseCode, "Ds_AuthorisationCode", "123456"));
        String params = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String signature = new RedsysSignature(key).sign(order, params).replace('+', '-').replace('/', '_');
        return new RedsysResultRequest(RedsysSignature.VERSION, params, signature);
    }

    private static RedsysResultRequest redsysResponse(String responseCode) {
        return redsysResponse(ORDER, "1530", responseCode, TestData.TEST_REDSYS_KEY);
    }

    // ------------------------------------------------------------------ iniciar

    @Test
    void iniciarPago_devuelveFormularioFirmadoYAnotaElPedido() {
        Reservation reservation = pendingReservation();
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        RedsysPaymentForm form = service.startPayment(8901L, TestData.CLIENT);

        assertThat(form.url()).isEqualTo("https://sis-t.redsys.es:25443/sis/realizarPago");
        assertThat(form.fields()).containsOnlyKeys("Ds_SignatureVersion", "Ds_MerchantParameters", "Ds_Signature");
        assertThat(form.order()).hasSize(12).startsWith("00008901");

        String decoded = new String(Base64.getDecoder().decode(form.fields().get("Ds_MerchantParameters")), StandardCharsets.UTF_8);
        assertThat(decoded).contains("\"DS_MERCHANT_AMOUNT\":\"1530\"", "\"DS_MERCHANT_CURRENCY\":\"978\"",
                "\"DS_MERCHANT_MERCHANTCODE\":\"999008881\"", "\"DS_MERCHANT_TRANSACTIONTYPE\":\"0\"",
                "\"DS_MERCHANT_ORDER\":\"" + form.order() + "\"");
        // La firma es la que Redsys espera con la clave del terminal
        assertThat(REDSYS.verify(form.order(), form.fields().get("Ds_MerchantParameters"),
                form.fields().get("Ds_Signature"))).isTrue();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getPaymentOrder()).isEqualTo(form.order());
        assertThat(reservation.getPaymentStartedAt()).isEqualTo(TestData.NOW);
        // Caducaba en 5 min, pero mientras paga se mantiene 15 min desde el inicio del pago
        assertThat(form.paymentDeadline()).isEqualTo(TestData.NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void iniciarPago_conPasarelaSimulada_409() {
        RedsysPaymentService simulated = buildService(TestData.redsysProperties(), "simulated");

        assertThatThrownBy(() -> simulated.startPayment(8901L, TestData.CLIENT))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("no está activo");
    }

    @Test
    void iniciarPago_sinClaveConfigurada_503() {
        RedsysProperties noKey = new RedsysProperties("https://sis-t.redsys.es:25443/sis/realizarPago",
                "999008881", "1", "", "978", "http://ok", "http://ko", "");
        RedsysPaymentService notConfigured = buildService(noKey, "redsys");

        assertThatThrownBy(() -> notConfigured.startPayment(8901L, TestData.CLIENT))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("no está configurada");
    }

    @Test
    void iniciarPago_conTtlVencido_400YLiberaButacas() {
        Reservation reservation = pendingReservation();
        reservation.setExpiresAt(TestData.NOW.minusSeconds(1));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.startPayment(8901L, TestData.CLIENT))
                .isInstanceOf(ReservationExpiredException.class);
        verify(seatLockService).release(List.of(reservation), ReservationStatus.EXPIRED, TestData.NOW);
    }

    @Test
    void iniciarPago_reservaDeOtroUsuario_403() {
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(pendingReservation()));

        assertThatThrownBy(() -> service.startPayment(8901L, TestData.OTHER_CLIENT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // ------------------------------------------------------------------ confirmar

    @Test
    void confirmar_pagoAutorizado_emiteEntradasYEvento() {
        Reservation reservation = paymentStarted();
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        var confirmation = service.confirmPayment(8901L, redsysResponse("0000"), TestData.CLIENT);

        assertThat(confirmation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmation.tickets()).hasSize(2).allMatch(code -> code.startsWith("QR-8901-8-1"));
        assertThat(reservation.getPaymentGateway()).isEqualTo("REDSYS");
        assertThat(reservation.getPaymentReference()).isEqualTo(ORDER);
        assertThat(reservation.getPaymentStartedAt()).isNull();
        verify(outboxService).enqueuePaymentCompleted(reservation);
    }

    @Test
    void confirmar_codigos0000a0099SonAutorizados() {
        Reservation reservation = paymentStarted();
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        service.confirmPayment(8901L, redsysResponse("0099"), TestData.CLIENT);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirmar_pagoDenegado_402YLaReservaSiguePendiente() {
        Reservation reservation = paymentStarted();
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.confirmPayment(8901L, redsysResponse("0190"), TestData.CLIENT))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessageContaining("0190");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getPaymentStartedAt()).isNull(); // vuelve a contar solo su TTL
        verify(outboxService, never()).enqueuePaymentCompleted(any());
    }

    @Test
    void confirmar_firmaFalsa_400() {
        // Respuesta "firmada" con otra clave: alguien intenta inventarse un pago aprobado
        RedsysResultRequest forged = redsysResponse(ORDER, "1530", "0000", "c3VwZXJzZWNyZXRhY2xhdmVkZXBydWVi");

        assertThatThrownBy(() -> service.confirmPayment(8901L, forged, TestData.CLIENT))
                .isInstanceOf(InvalidPaymentResponseException.class)
                .hasMessageContaining("firma");
        verify(reservationRepository, never()).findByPaymentOrderForUpdate(any());
    }

    @Test
    void confirmar_importeDistinto_400() {
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(paymentStarted()));
        RedsysResultRequest otherAmount = redsysResponse(ORDER, "100", "0000", TestData.TEST_REDSYS_KEY);

        assertThatThrownBy(() -> service.confirmPayment(8901L, otherAmount, TestData.CLIENT))
                .isInstanceOf(InvalidPaymentResponseException.class)
                .hasMessageContaining("importe");
    }

    @Test
    void confirmar_respuestaDeOtraReserva_400() {
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(paymentStarted()));

        assertThatThrownBy(() -> service.confirmPayment(1234L, redsysResponse("0000"), TestData.CLIENT))
                .isInstanceOf(InvalidPaymentResponseException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void confirmar_intentoAnteriorQueLlegaTarde_400() {
        Reservation reservation = paymentStarted();
        reservation.setPaymentOrder("000089015678"); // ya se inició otro intento después
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.confirmPayment(8901L, redsysResponse("0000"), TestData.CLIENT))
                .isInstanceOf(InvalidPaymentResponseException.class)
                .hasMessageContaining("último intento");
    }

    @Test
    void confirmar_respuestaRepetida_esIdempotente() {
        Reservation reservation = paymentStarted();
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        var first = service.confirmPayment(8901L, redsysResponse("0000"), TestData.CLIENT);
        var second = service.confirmFromRedsys(RedsysSignature.VERSION,
                redsysResponse("0000").merchantParameters(), redsysResponse("0000").signature());

        assertThat(second.tickets()).isEqualTo(first.tickets());
        verify(outboxService, times(1)).enqueuePaymentCompleted(reservation); // un solo evento
    }

    @Test
    void confirmar_reservaYaCerrada_409() {
        Reservation reservation = paymentStarted();
        reservation.setStatus(ReservationStatus.EXPIRED);
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.confirmPayment(8901L, redsysResponse("0000"), TestData.CLIENT))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void confirmar_versionDeFirmaDesconocida_400() {
        RedsysResultRequest response = redsysResponse("0000");
        RedsysResultRequest otherVersion = new RedsysResultRequest("HMAC_SHA1_V0",
                response.merchantParameters(), response.signature());

        assertThatThrownBy(() -> service.confirmPayment(8901L, otherVersion, TestData.CLIENT))
                .isInstanceOf(InvalidPaymentResponseException.class);
    }

    @Test
    void retornoDelNavegador_sinJwt_confirmaPorLaFirma() {
        Reservation reservation = paymentStarted();
        when(reservationRepository.findByPaymentOrderForUpdate(ORDER)).thenReturn(Optional.of(reservation));
        RedsysResultRequest response = redsysResponse("0000");

        var confirmation = service.confirmFromRedsys(response.signatureVersion(),
                response.merchantParameters(), response.signature());

        assertThat(confirmation.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }
}
