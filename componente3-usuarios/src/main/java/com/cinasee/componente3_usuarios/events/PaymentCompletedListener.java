package com.cinasee.componente3_usuarios.events;

import com.cinasee.componente3_usuarios.services.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del evento PaymentCompleted.
 *
 * La cola se declara en RabbitMQConfig. Si el procesamiento lanza una excepción,
 * Spring AMQP reintenta según spring.rabbitmq.listener.simple.retry y, agotados
 * los intentos, el mensaje se envía a la cola de mensajes muertos (DLQ).
 */
@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final LoyaltyService loyaltyService;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        loyaltyService.processPaymentCompleted(event);
    }
}
