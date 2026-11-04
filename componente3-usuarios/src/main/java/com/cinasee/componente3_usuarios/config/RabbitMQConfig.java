package com.cinasee.componente3_usuarios.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Topología RabbitMQ del componente de usuarios.
 *
 * Exchange principal (topic) -> cola de pagos completados.
 * Si un mensaje no se puede procesar tras los reintentos, va a una DLQ para
 * poder inspeccionarlo sin perderlo.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange paymentExchange(
            @Value("${app.rabbitmq.exchange}") String exchange
    ) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue paymentCompletedQueue(
            @Value("${app.rabbitmq.queue}") String queue,
            @Value("${app.rabbitmq.dead-letter-exchange}") String deadLetterExchange,
            @Value("${app.rabbitmq.dead-letter-routing-key}") String deadLetterRoutingKey
    ) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Binding paymentCompletedBinding(
            Queue paymentCompletedQueue,
            TopicExchange paymentExchange,
            @Value("${app.rabbitmq.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(paymentCompletedQueue)
                .to(paymentExchange)
                .with(routingKey);
    }

    @Bean
    public TopicExchange paymentDeadLetterExchange(
            @Value("${app.rabbitmq.dead-letter-exchange}") String exchange
    ) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue paymentCompletedDeadLetterQueue(
            @Value("${app.rabbitmq.dead-letter-queue}") String queue
    ) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding paymentCompletedDeadLetterBinding(
            Queue paymentCompletedDeadLetterQueue,
            TopicExchange paymentDeadLetterExchange,
            @Value("${app.rabbitmq.dead-letter-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(paymentCompletedDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(routingKey);
    }

    /**
     * Sin este converter Spring AMQP usa SimpleMessageConverter y solo entiende
     * String/byte[]; el JSON del evento no se podría deserializar al record.
     */
    @Bean
    public JacksonJsonMessageConverter messageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // Solo se permite deserializar tipos de nuestro dominio (evita gadgets).
        converter.getJavaTypeMapper().addTrustedPackages(
                "com.cinasee.componente3_usuarios.events");
        return converter;
    }
}
