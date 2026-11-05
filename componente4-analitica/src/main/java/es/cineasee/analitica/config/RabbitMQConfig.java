package es.cineasee.analitica.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:payment.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue:payment.completed.queue}")
    private String queueName;

    @Value("${app.rabbitmq.routing-key:payment.completed}")
    private String routingKey;

    @Bean
    public Queue paymentCompletedQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Binding paymentBinding(Queue paymentCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
