package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.config.MessagingProperties;
import com.cinasee.componente2_reservas.models.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Publicador de eventos sobre RabbitMQ (Spring AMQP).
 *
 * <ul>
 *   <li>Exchange topic {@code payment.exchange} y routing key del evento
 *       (p. ej. {@code payment.completed}), los mismos que enlaza el Componente 3.</li>
 *   <li>Mensaje persistente (sobrevive a un reinicio del broker si la cola es durable).</li>
 *   <li>content-type {@code application/json} y SIN cabecera __TypeId__: el consumidor
 *       deduce el tipo a partir de su propio parámetro (@RabbitListener), así no se
 *       comparten nombres de clases Java entre microservicios.</li>
 *   <li><b>Publisher confirms</b>: se espera a que el broker confirme que ha recibido
 *       el mensaje. Si no confirma, se lanza una excepción y el outbox lo reintentará.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @Override
    public void publish(OutboxEvent event) {
        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId())
                .setType(event.getEventType())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateId", event.getAggregateId())
                .build();

        // invoke() ejecuta las operaciones en un mismo canal, necesario para usar
        // waitForConfirmsOrDie (requiere spring.rabbitmq.publisher-confirm-type=simple)
        rabbitTemplate.invoke(operations -> {
            operations.send(properties.exchange(), event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(properties.confirmTimeoutMs());
            return Boolean.TRUE;
        });
    }
}
