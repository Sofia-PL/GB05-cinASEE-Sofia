package es.cineasee.analitica.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de CONTRATO con el productor del evento (Componente 2, Reservas).
 *
 * El JSON es una copia del mensaje que publica el Componente 2 en
 * payment.exchange. Se convierte igual que lo hace Spring AMQP con el
 * parámetro del @RabbitListener (JacksonJsonMessageConverter, sin cabecera
 * __TypeId__). Si el Componente 2 cambia su evento, hay que actualizar este JSON.
 */
class PaymentCompletedContractTest {

    private static final String RESERVAS_JSON = """
            {
              "eventId": "evt-1",
              "reservationId": 8901,
              "userId": 5590,
              "movieId": "movie-101",
              "amount": 15.30,
              "currency": "EUR",
              "purchaseDate": "2026-10-08T12:03:00Z",
              "occurredAt": "2026-10-08T12:03:01Z",
              "showId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
              "sessionId": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
              "roomId": "room-1",
              "ticketsCount": 2,
              "totalAmount": 15.30,
              "showStartTime": "2026-10-16T18:30:00Z",
              "timestamp": "2026-10-16T20:30:00"
            }
            """;

    private static PaymentCompletedEvent deliver(String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .build();
        message.getMessageProperties().setInferredArgumentType(PaymentCompletedEvent.class);
        return (PaymentCompletedEvent) new JacksonJsonMessageConverter().fromMessage(message);
    }

    @Test
    void reservasMessageIsUnderstoodByAnalytics() {
        PaymentCompletedEvent event = deliver(RESERVAS_JSON);

        assertThat(event.getReservationId()).isEqualTo(8901L);
        assertThat(event.getUserId()).isEqualTo(5590L);
        assertThat(event.getMovieId()).isEqualTo("movie-101");
        assertThat(event.getSessionId()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(event.getRoomId()).isEqualTo("room-1");
        assertThat(event.getTicketsCount()).isEqualTo(2);
        assertThat(event.getTotalAmount()).isEqualTo(15.30);
        assertThat(event.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 10, 16, 20, 30));
    }

    @Test
    void fieldsReservasDoesNotSendAreNull() {
        PaymentCompletedEvent event = deliver(RESERVAS_JSON);

        // Se completan después con Cartelera o con valores por defecto en el listener
        assertThat(event.getMovieTitle()).isNull();
        assertThat(event.getGenre()).isNull();
        assertThat(event.getCinemaId()).isNull();
    }

    @Test
    void guestPurchaseHasNoUserId() {
        PaymentCompletedEvent event = deliver(RESERVAS_JSON.replace("\"userId\": 5590", "\"userId\": null"));

        assertThat(event.getUserId()).isNull();
        assertThat(event.getReservationId()).isEqualTo(8901L);
    }
}
