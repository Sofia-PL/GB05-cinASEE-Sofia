package com.cinasee.componente3_usuarios.config;

import com.cinasee.componente3_usuarios.events.PaymentCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void mainQueueIsDurableAndHasDeadLetterConfiguration() {
        Queue queue = config.paymentCompletedQueue(
                "user.payment.completed",
                "payment.exchange.dlx",
                "payment.completed.dlq"
        );

        assertTrue(queue.isDurable());
        assertEquals("payment.exchange.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("payment.completed.dlq", queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void exchangeAndBindingUseConfiguredNames() {
        TopicExchange exchange = config.paymentExchange("payment.exchange");
        Queue queue = config.paymentCompletedQueue(
                "user.payment.completed",
                "payment.exchange.dlx",
                "payment.completed.dlq"
        );
        Binding binding = config.paymentCompletedBinding(queue, exchange, "payment.completed");

        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
        assertEquals("payment.exchange", binding.getExchange());
        assertEquals("user.payment.completed", binding.getDestination());
        assertEquals("payment.completed", binding.getRoutingKey());
    }

    @Test
    void converterDeserializesPaymentCompletedJson() {
        JacksonJsonMessageConverter converter = config.messageConverter(JsonMapper.builder().build());
        String json = """
                {
                  "eventId": "evt-8901",
                  "reservationId": 8901,
                  "userId": 42,
                  "movieId": "movie-1024",
                  "amount": 18.50,
                  "currency": "EUR",
                  "purchaseDate": "2026-09-20T18:30:00Z",
                  "occurredAt": "2026-09-20T18:30:05Z",
                  "campoDesconocido": "se ignora"
                }
                """;

        Message message = new Message(
                json.getBytes(StandardCharsets.UTF_8),
                MessagePropertiesBuilder.newInstance()
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        // En producción el listener aporta el tipo del parámetro;
                        // aquí simulamos esa pista con la cabecera estándar de Spring AMQP.
                        .setHeader("__TypeId__", PaymentCompletedEvent.class.getName())
                        .build()
        );

        PaymentCompletedEvent event = (PaymentCompletedEvent) converter.fromMessage(message);

        assertEquals("evt-8901", event.eventId());
        assertEquals(8901L, event.reservationId());
        assertEquals(42L, event.userId());
        assertEquals("movie-1024", event.movieId());
        assertEquals(new BigDecimal("18.50"), event.amount());
        assertEquals(OffsetDateTime.parse("2026-09-20T18:30:00Z"), event.purchaseDate());
    }
}
