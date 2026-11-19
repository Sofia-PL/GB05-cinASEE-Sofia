package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.TestData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de CONTRATO del evento PaymentCompleted con sus dos consumidores.
 *
 * No depende del código de los otros componentes: copia aquí la forma de sus
 * clases de evento (mismos nombres y tipos de campo) y deserializa nuestro
 * mensaje igual que lo harían sus @RabbitListener, con JacksonJsonMessageConverter
 * y el tipo deducido del parámetro del listener (no hay cabecera __TypeId__).
 *
 * Si un compañero cambia su clase de evento, hay que actualizar la copia de este
 * test; si este test falla, el otro componente dejaría de entender nuestros mensajes.
 */
class PaymentCompletedContractTest {

    /** Copia del record del Componente 3 (componente3_usuarios.events.PaymentCompletedEvent). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsuariosPaymentCompletedEvent(
            String eventId, Long reservationId, Long userId, String movieId, BigDecimal amount,
            String currency, OffsetDateTime purchaseDate, OffsetDateTime occurredAt) {
    }

    /** Copia de la clase del Componente 4 (es.cineasee.analitica.events.PaymentCompletedEvent). */
    @Data
    @NoArgsConstructor
    static class AnaliticaPaymentCompletedEvent {
        private Long reservationId;
        private Long userId;
        private String cinemaId;
        private String roomId;
        private String sessionId;
        private String movieId;
        private String movieTitle;
        private String genre;
        private String distributor;
        private Long distributorId;
        private Integer ticketsCount;
        private Double totalAmount;
        private LocalDateTime timestamp;
    }

    /** Evento tal como lo construye OutboxService: sesión el viernes 16/10/2026 a las 18:30 UTC. */
    private static final PaymentCompletedEvent EVENT = new PaymentCompletedEvent(
            "evt-1", 8901L, 5590L, "movie-101", new BigDecimal("15.30"), "EUR",
            Instant.parse("2026-10-08T12:03:00Z"), Instant.parse("2026-10-08T12:03:01Z"),
            "01ARZ3NDEKTSV4RRFFQ69G5FAV", "01ARZ3NDEKTSV4RRFFQ69G5FAV", "room-1", 2, new BigDecimal("15.30"),
            Instant.parse("2026-10-16T18:30:00Z"),
            LocalDateTime.ofInstant(Instant.parse("2026-10-16T18:30:00Z"), ZoneId.of("Europe/Madrid")));

    /** Construye el mensaje AMQP igual que RabbitEventPublisher y lo convierte al tipo del consumidor. */
    private static Object deliverTo(Class<?> listenerParameterType) {
        String json = JsonMapper.builder().build().writeValueAsString(EVENT);
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .build();
        // Lo que hace Spring AMQP con el parámetro del @RabbitListener
        message.getMessageProperties().setInferredArgumentType(listenerParameterType);
        return new JacksonJsonMessageConverter().fromMessage(message);
    }

    @Test
    void componente3_recibeLosDatosParaSumarPuntos() {
        var received = (UsuariosPaymentCompletedEvent) deliverTo(UsuariosPaymentCompletedEvent.class);

        assertThat(received.reservationId()).isEqualTo(8901L);
        assertThat(received.userId()).isEqualTo(5590L);
        assertThat(received.movieId()).isEqualTo("movie-101");
        assertThat(received.amount()).isEqualByComparingTo("15.30");
        assertThat(received.purchaseDate().toInstant()).isEqualTo(Instant.parse("2026-10-08T12:03:00Z"));
    }

    @Test
    void componente4_recibeLosDatosParaOcupacionYTaquilla() {
        var received = (AnaliticaPaymentCompletedEvent) deliverTo(AnaliticaPaymentCompletedEvent.class);

        assertThat(received.getReservationId()).isEqualTo(8901L);
        assertThat(received.getSessionId()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(received.getRoomId()).isEqualTo("room-1");
        assertThat(received.getMovieId()).isEqualTo("movie-101");
        assertThat(received.getTicketsCount()).isEqualTo(2);
        assertThat(received.getTotalAmount()).isEqualTo(15.30);
        // Hora local de la sesión en Madrid (UTC+2 en octubre): viernes a las 20:30
        assertThat(received.getTimestamp()).isEqualTo(LocalDateTime.parse("2026-10-16T20:30:00"));
        assertThat(received.getTimestamp().getDayOfWeek().getValue()).isEqualTo(5);
    }

    @Test
    void compraDeInvitado_userIdNuloEnAmbos() {
        PaymentCompletedEvent guest = new PaymentCompletedEvent(
                "evt-2",                 1L, null, "movie-101", BigDecimal.TEN, "EUR", TestData.NOW, TestData.NOW,
                "01ARZ3NDEKTSV4RRFFQ69G5FAV", "01ARZ3NDEKTSV4RRFFQ69G5FAV", "room-1", 1, BigDecimal.TEN, TestData.NOW, LocalDateTime.parse("2026-10-08T14:00:00"));
        String json = JsonMapper.builder().build().writeValueAsString(guest);

        assertThat(json).contains("\"userId\":null");
    }
}
