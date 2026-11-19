package com.cinasee.componente2_reservas.controllers;

import com.cinasee.componente2_reservas.config.SecurityConfig;
import com.cinasee.componente2_reservas.dto.BoxOfficeDataResponse;
import com.cinasee.componente2_reservas.dto.ReservationResponse;
import com.cinasee.componente2_reservas.dto.SeatMapResponse;
import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.DiscountType;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.CancellationService;
import com.cinasee.componente2_reservas.dto.RedsysPaymentForm;
import com.cinasee.componente2_reservas.services.PaymentService;
import com.cinasee.componente2_reservas.services.RedsysPaymentService;
import com.cinasee.componente2_reservas.services.ReportService;
import com.cinasee.componente2_reservas.services.ReservationService;
import com.cinasee.componente2_reservas.services.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la capa web: seguridad (JWT y roles), validación de entrada (@Valid) y
 * traducción de excepciones a códigos HTTP del OpenAPI. Los servicios son mocks.
 */
@WebMvcTest({ReservationController.class, ReportController.class, TicketController.class, RedsysReturnController.class})
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private CancellationService cancellationService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private TicketService ticketService;
    @MockitoBean
    private RedsysPaymentService redsysPaymentService;
    /** No se usa (jwt() inyecta el token ya validado) pero el Resource Server necesita el bean. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String VALID_BODY = """
            {"showId": "01ARZ3NDEKTSV4RRFFQ69G5FAV", "userId": 5590, "seats": [{"row": 8, "column": 12}, {"row": 8, "column": 13}]}
            """;

    /** Simula un JWT del Componente 3 con el sub y el rol indicados. */
    private static JwtRequestPostProcessor token(String subject, String role) {
        return jwt().jwt(j -> j.subject(subject).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static ReservationResponse sampleReservation() {
        return new ReservationResponse(8901L, "01ARZ3NDEKTSV4RRFFQ69G5FAV", "movie-101", "room-1", Instant.parse("2026-10-16T18:30:00Z"), 5590L, null,
                new BigDecimal("9.25"), new BigDecimal("18.50"), "EUR", DiscountType.NONE,
                ReservationStatus.PENDING_PAYMENT, Instant.parse("2026-10-08T11:54:36Z"),
                Instant.parse("2026-10-08T12:04:36Z"), null, 2, List.of());
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    void sinToken_401() throws Exception {
        mockMvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapaDeButacas_esPublico() throws Exception {
        when(reservationService.getSeatMap("01ARZ3NDEKTSV4RRFFQ69G5FAV")).thenReturn(new SeatMapResponse("01ARZ3NDEKTSV4RRFFQ69G5FAV",
                List.of(new SeatMapResponse.OccupiedSeat(8, 12, SeatStatus.SOLD))));

        mockMvc.perform(get("/reservations/shows/01ARZ3NDEKTSV4RRFFQ69G5FAV/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedSeats[0].row").value(8))
                .andExpect(jsonPath("$.occupiedSeats[0].status").value("SOLD"));
    }

    @Test
    void estadisticas_prohibidasParaClientes() throws Exception {
        mockMvc.perform(get("/reservations/stats").with(token("5590", "CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void boxOffice_permitidoParaAdmin() throws Exception {
        when(reportService.getBoxOfficeData(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"), null))
                .thenReturn(new BoxOfficeDataResponse(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"), "EUR",
                        List.of(new BoxOfficeDataResponse.MovieBoxOffice("movie-101", 1250, 600, new BigDecimal("11250.50")))));

        mockMvc.perform(get("/reservations/box-office-data")
                        .param("startDate", "2026-10-01").param("endDate", "2026-10-07")
                        .with(token("1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies[0].grossRevenue").value(11250.50));
    }

    @Test
    void checkIn_prohibidoParaClientes() throws Exception {
        mockMvc.perform(post("/reservations/tickets/QR-1-1-1-ABC/check-in").with(token("5590", "CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- crear reserva

    @Test
    void crearReserva_201ConLocation() throws Exception {
        when(reservationService.createReservation(any(), any())).thenReturn(sampleReservation());

        mockMvc.perform(post("/reservations").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/reservations/8901"))
                .andExpect(jsonPath("$.id").value(8901))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(18.50))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-08T12:04:36Z"));

        // La identidad que llega al servicio sale del JWT, no del cuerpo de la petición
        verify(reservationService).createReservation(any(), eq(new CallerIdentity("5590", "CLIENTE_REGISTRADO")));
    }

    @Test
    void crearReserva_validacion400() throws Exception {
        String invalid = """
                {"seats": [{"row": 0, "column": 12}]}
                """;
        mockMvc.perform(post("/reservations").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(2)); // showId nulo y fila 0
    }

    @Test
    void crearReserva_masDe10Butacas400() throws Exception {
        StringBuilder seats = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            seats.append(i > 1 ? "," : "").append("{\"row\":1,\"column\":").append(i).append('}');
        }
        mockMvc.perform(post("/reservations").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"01ARZ3NDEKTSV4RRFFQ69G5FAV\",\"seats\":[" + seats + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearReserva_butacasOcupadas409ConDetalle() throws Exception {
        when(reservationService.createReservation(any(), any()))
                .thenThrow(new SeatsUnavailableException(List.of("8-12")));

        mockMvc.perform(post("/reservations").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"))
                .andExpect(jsonPath("$.details[0]").value("8-12"));
    }

    @Test
    void crearReserva_dependenciaCaida503() throws Exception {
        when(reservationService.createReservation(any(), any()))
                .thenThrow(new ServiceUnavailableException("El servicio de Cartelera no está disponible", null));

        mockMvc.perform(post("/reservations").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    // ---------------------------------------------------------------- pago

    @Test
    void pagar_200ConEntradas() throws Exception {
        when(paymentService.payReservation(eq(8901L), any(), any())).thenReturn(new TicketConfirmation(
                8901L, ReservationStatus.CONFIRMED, new BigDecimal("18.50"), "EUR", "SIM-1",
                List.of("QR-8901-8-12-ABCDEFGH", "QR-8901-8-13-JKLMNPQR")));

        mockMvc.perform(post("/reservations/8901/payment").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gateway\":\"REDSYS\",\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.tickets.length()").value(2));
    }

    @Test
    void pagar_pasarelaNoSoportada400() throws Exception {
        mockMvc.perform(post("/reservations/8901/payment").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gateway\":\"PAYPAL\",\"paymentToken\":\"tok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagar_ttlCaducado400() throws Exception {
        when(paymentService.payReservation(eq(8901L), any(), any())).thenThrow(new ReservationExpiredException(8901L));

        mockMvc.perform(post("/reservations/8901/payment").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gateway\":\"REDSYS\",\"paymentToken\":\"tok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    // ---------------------------------------------------------------- Redsys

    private static TicketConfirmation confirmed() {
        return new TicketConfirmation(8901L, ReservationStatus.CONFIRMED, new BigDecimal("15.30"), "EUR",
                "000089011234", List.of("QR-8901-8-12-ABCDEFGH"));
    }

    @Test
    void redsys_iniciarPago_devuelveElFormulario() throws Exception {
        when(redsysPaymentService.startPayment(eq(8901L), any())).thenReturn(new RedsysPaymentForm(8901L,
                "https://sis-t.redsys.es:25443/sis/realizarPago",
                java.util.Map.of("Ds_SignatureVersion", "HMAC_SHA256_V1", "Ds_MerchantParameters", "eyJ9", "Ds_Signature", "abc="),
                "000089011234", new BigDecimal("15.30"), "EUR", Instant.parse("2026-10-08T12:15:00Z")));

        mockMvc.perform(post("/reservations/8901/payment/redsys").with(token("5590", "CLIENTE_REGISTRADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://sis-t.redsys.es:25443/sis/realizarPago"))
                .andExpect(jsonPath("$.fields.Ds_SignatureVersion").value("HMAC_SHA256_V1"))
                .andExpect(jsonPath("$.order").value("000089011234"));
    }

    @Test
    void redsys_iniciarPagoSinToken_401() throws Exception {
        mockMvc.perform(post("/reservations/8901/payment/redsys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redsys_confirmar_usaLosNombresDeParametrosDeRedsys() throws Exception {
        when(redsysPaymentService.confirmPayment(eq(8901L), any(), any())).thenReturn(confirmed());

        mockMvc.perform(post("/reservations/8901/payment/redsys/confirm").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Ds_SignatureVersion\":\"HMAC_SHA256_V1\",\"Ds_MerchantParameters\":\"eyJ9\",\"Ds_Signature\":\"abc=\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void redsys_confirmarSinParametros_400() throws Exception {
        mockMvc.perform(post("/reservations/8901/payment/redsys/confirm").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void redsys_retornoDelNavegador_esPublicoYMuestraLasEntradas() throws Exception {
        when(redsysPaymentService.confirmFromRedsys("HMAC_SHA256_V1", "eyJ9", "abc")).thenReturn(confirmed());

        mockMvc.perform(get("/reservations/payments/redsys/return")
                        .param("Ds_SignatureVersion", "HMAC_SHA256_V1")
                        .param("Ds_MerchantParameters", "eyJ9")
                        .param("Ds_Signature", "abc"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("QR-8901-8-12-ABCDEFGH")));
    }

    @Test
    void redsys_retornoSinParametros_reservaPendiente() throws Exception {
        mockMvc.perform(get("/reservations/payments/redsys/return"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Pago no completado")));
    }

    @Test
    void redsys_retornoConPagoDenegado_402() throws Exception {
        when(redsysPaymentService.confirmFromRedsys(any(), any(), any()))
                .thenThrow(new PaymentDeclinedException("Redsys ha denegado la operación (código 0190)"));

        mockMvc.perform(get("/reservations/payments/redsys/return")
                        .param("Ds_SignatureVersion", "HMAC_SHA256_V1")
                        .param("Ds_MerchantParameters", "eyJ9")
                        .param("Ds_Signature", "abc"))
                .andExpect(status().isPaymentRequired())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Pago rechazado")));
    }

    @Test
    void pagar_rechazado402() throws Exception {
        when(paymentService.payReservation(eq(8901L), any(), any())).thenThrow(new PaymentDeclinedException("fondos"));

        mockMvc.perform(post("/reservations/8901/payment").with(token("5590", "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gateway\":\"REDSYS\",\"paymentToken\":\"tok_declined\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
    }
}
