package com.cinasee.componente2_reservas.events;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.models.OutboxEvent;
import com.cinasee.componente2_reservas.repositories.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del relay del outbox: publica, marca como publicado y reintenta si el broker falla.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private EventPublisher publisher;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, publisher, TestData.FIXED_CLOCK);
    }

    private static OutboxEvent event(String id) {
        return OutboxEvent.builder().id(id).aggregateType("Reservation").aggregateId("8901")
                .eventType("PaymentCompleted").routingKey("payment.completed").payload("{}")
                .createdAt(TestData.NOW).attempts(0).build();
    }

    @Test
    void publicaLosEventosPendientesYLosMarca() {
        OutboxEvent first = event("e1");
        OutboxEvent second = event("e2");
        when(repository.findPending(any())).thenReturn(List.of(first, second));

        int published = relay.publishPendingEvents();

        assertThat(published).isEqualTo(2);
        assertThat(first.getPublishedAt()).isEqualTo(TestData.NOW);
        assertThat(second.getPublishedAt()).isEqualTo(TestData.NOW);
        verify(publisher).publish(first);
        verify(publisher).publish(second);
    }

    @Test
    void brokerCaido_anotaElErrorYDetieneElLote() {
        OutboxEvent first = event("e1");
        OutboxEvent second = event("e2");
        when(repository.findPending(any())).thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(publisher).publish(first);

        int published = relay.publishPendingEvents();

        assertThat(published).isZero();
        assertThat(first.getPublishedAt()).isNull();      // sigue pendiente → se reintentará
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("Connection refused");
        verify(publisher, never()).publish(second);       // no se insiste con el broker caído
    }
}
