package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.models.OutboxEvent;

/**
 * Publica un evento ya serializado en el bus de mensajes.
 * Abstracción que permite probar el {@link OutboxRelay} sin un RabbitMQ real.
 */
public interface EventPublisher {

    /**
     * @throws RuntimeException si el broker no confirma la recepción del mensaje
     */
    void publish(OutboxEvent event);
}
