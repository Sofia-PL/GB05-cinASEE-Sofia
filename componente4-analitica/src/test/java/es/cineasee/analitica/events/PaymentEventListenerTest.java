package es.cineasee.analitica.events;

import es.cineasee.analitica.clients.CarteleraClient;
import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.models.AnalyticsEvent;
import es.cineasee.analitica.repositories.AnalyticsEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del consumidor del evento PaymentCompleted (RabbitMQ).
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    /** Viernes 16/10/2026 a las 18:30. */
    private static final LocalDateTime FRIDAY_1830 = LocalDateTime.of(2026, 10, 16, 18, 30);

    @Mock
    private AnalyticsEventRepository repository;
    @Mock
    private CarteleraClient carteleraClient;

    @InjectMocks
    private PaymentEventListener listener;

    private AnalyticsEvent savedEvent() {
        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void completeEventIsPersistedWithoutCallingCartelera() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .reservationId(8901L).userId(5590L).cinemaId("cinema-1").roomId("room-1")
                .sessionId("show-1").movieId("m1").movieTitle("Dune").genre("SCI-FI")
                .distributor("Warner").distributorId(3L).ticketsCount(2).totalAmount(18.5)
                .timestamp(FRIDAY_1830).build();

        listener.handlePaymentCompleted(event);

        AnalyticsEvent saved = savedEvent();
        assertThat(saved.getReservationId()).isEqualTo(8901L);
        assertThat(saved.getUserId()).isEqualTo(5590L);
        assertThat(saved.getCinemaId()).isEqualTo("cinema-1");
        assertThat(saved.getRoomId()).isEqualTo("room-1");
        assertThat(saved.getSessionId()).isEqualTo("show-1");
        assertThat(saved.getMovieTitle()).isEqualTo("Dune");
        assertThat(saved.getGenre()).isEqualTo("SCI-FI");
        assertThat(saved.getDistributor()).isEqualTo("Warner");
        assertThat(saved.getDistributorId()).isEqualTo(3L);
        assertThat(saved.getTicketsSold()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualTo(18.5);
        assertThat(saved.getDayOfWeek()).isEqualTo(5);
        assertThat(saved.getSessionTime()).isEqualTo("18:30");
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(saved.getEventTimestamp()).isEqualTo(FRIDAY_1830);
        verify(carteleraClient, never()).getMovieById(any());
    }

    @Test
    void missingMovieDataIsEnrichedFromCartelera() {
        when(carteleraClient.getMovieById("m1")).thenReturn(MovieDTO.builder()
                .id("m1").title("Dune").genre("SCI-FI").distributor("Warner").build());
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .reservationId(8901L).movieId("m1").timestamp(FRIDAY_1830).build();

        listener.handlePaymentCompleted(event);

        AnalyticsEvent saved = savedEvent();
        assertThat(saved.getMovieTitle()).isEqualTo("Dune");
        assertThat(saved.getGenre()).isEqualTo("SCI-FI");
        assertThat(saved.getDistributor()).isEqualTo("Warner");
    }

    @Test
    void carteleraFailureStillPersistsEventWithPlaceholderTitle() {
        when(carteleraClient.getMovieById("m1")).thenThrow(new RuntimeException("C1 caído"));
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .reservationId(8901L).movieId("m1").timestamp(FRIDAY_1830).build();

        listener.handlePaymentCompleted(event);

        AnalyticsEvent saved = savedEvent();
        assertThat(saved.getMovieTitle()).isEqualTo("Película #m1");
        assertThat(saved.getGenre()).isNull();
    }

    @Test
    void missingOptionalFieldsGetDefaults() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .reservationId(8901L).movieTitle("Dune").genre("SCI-FI").build();

        listener.handlePaymentCompleted(event);

        AnalyticsEvent saved = savedEvent();
        assertThat(saved.getCinemaId()).isEqualTo("1");
        assertThat(saved.getTicketsSold()).isEqualTo(1);
        assertThat(saved.getTotalAmount()).isZero();
        assertThat(saved.getEventTimestamp()).isNotNull();
        assertThat(saved.getDayOfWeek()).isBetween(1, 7);
        verify(carteleraClient, never()).getMovieById(any());
    }

    @Test
    void persistenceErrorIsNotPropagatedToTheBroker() {
        when(repository.save(any())).thenThrow(new RuntimeException("BD caída"));
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .reservationId(8901L).movieTitle("Dune").genre("SCI-FI").timestamp(FRIDAY_1830).build();

        assertThatCode(() -> listener.handlePaymentCompleted(event)).doesNotThrowAnyException();
    }
}
