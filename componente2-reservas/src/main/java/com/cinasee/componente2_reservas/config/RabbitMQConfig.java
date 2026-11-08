package com.cinasee.componente2_reservas.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Topología RabbitMQ del productor (Componente 2).
 *
 * Reservas solo PUBLICA eventos, así que únicamente declara el exchange. Las colas
 * las declara cada consumidor (Componente 3: user.payment.completed; Componente 4:
 * la suya propia), que es quien sabe qué eventos le interesan. Esto mantiene el
 * desacoplamiento: Reservas no conoce a sus consumidores.
 *
 * El exchange se declara con los MISMOS argumentos que en el Componente 3
 * (topic, durable, sin auto-delete). Si no coincidieran, RabbitMQ respondería
 * PRECONDITION_FAILED al segundo servicio que lo declarase.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange paymentExchange(MessagingProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /**
     * SOLO en el perfil "isolated": cola de depuración enlazada a todos los
     * eventos de pago (payment.#). Cuando probamos el microservicio aislado no hay
     * ningún consumidor (ni Comp. 3 ni Comp. 4), y RabbitMQ descartaría los
     * mensajes; con esta cola se pueden ver en la consola web (localhost:15672).
     */
    @Bean
    @Profile("isolated")
    public Queue debugPaymentEventsQueue() {
        return QueueBuilder.durable("reservas.debug.payment-events").build();
    }

    @Bean
    @Profile("isolated")
    public Binding debugPaymentEventsBinding(Queue debugPaymentEventsQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(debugPaymentEventsQueue).to(paymentExchange).with("payment.#");
    }
}
