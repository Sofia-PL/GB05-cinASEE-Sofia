package com.cinasee.componente2_reservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nombres de la topología RabbitMQ (prefijo {@code app.rabbitmq}).
 *
 * Deben coincidir con los que usa el Componente 3 (ver
 * componente3-usuarios/docs/evento-payment-completed.md): exchange topic durable
 * "payment.exchange" y routing key "payment.completed".
 *
 * @param exchange                  exchange topic donde se publican los eventos de pago
 * @param paymentCompletedRoutingKey routing key del evento PaymentCompleted
 * @param paymentRefundedRoutingKey  routing key del evento PaymentRefunded
 * @param confirmTimeoutMs          espera máxima del "publisher confirm" del broker
 */
@ConfigurationProperties(prefix = "app.rabbitmq")
public record MessagingProperties(
        @DefaultValue("payment.exchange") String exchange,
        @DefaultValue("payment.completed") String paymentCompletedRoutingKey,
        @DefaultValue("payment.refunded") String paymentRefundedRoutingKey,
        @DefaultValue("5000") long confirmTimeoutMs
) {
}
