package es.cineasee.analitica.events;

import es.cineasee.analitica.clients.CarteleraClient;
import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.models.AnalyticsEvent;
import es.cineasee.analitica.repositories.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final CarteleraClient carteleraClient;

    @RabbitListener(queues = "${app.rabbitmq.queue:payment.completed.queue}")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Evento PaymentCompleted recibido para la reserva ID: {}", event.getReservationId());

        try {
            String title = event.getMovieTitle();
            String genre = event.getGenre();
            String distributor = event.getDistributor();

            // Si los datos de la película no vienen en el payload, enriquecemos con CarteleraClient
            if ((title == null || genre == null) && event.getMovieId() != null) {
                try {
                    MovieDTO movie = carteleraClient.getMovieById(event.getMovieId());
                    if (movie != null) {
                        title = movie.getTitle();
                        genre = movie.getGenre();
                        distributor = movie.getDistributor();
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo obtener información adicional de la película ID {}: {}", event.getMovieId(), ex.getMessage());
                }
            }

            LocalDateTime eventTime = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
            int dayOfWeek = eventTime.getDayOfWeek().getValue(); // 1 = Lunes ... 7 = Domingo
            String sessionTime = String.format("%02d:%02d", eventTime.getHour(), eventTime.getMinute());

            AnalyticsEvent analyticsEvent = AnalyticsEvent.builder()
                    .reservationId(event.getReservationId())
                    .userId(event.getUserId())
                    .cinemaId(event.getCinemaId() != null ? event.getCinemaId() : "1")
                    .roomId(event.getRoomId())
                    .sessionId(event.getSessionId())
                    .movieId(event.getMovieId())
                    .movieTitle(title != null ? title : "Película #" + event.getMovieId())
                    .genre(genre)
                    .distributor(distributor)
                    .distributorId(event.getDistributorId())
                    .ticketsSold(event.getTicketsCount() != null ? event.getTicketsCount() : 1)
                    .totalAmount(event.getTotalAmount() != null ? event.getTotalAmount() : 0.0)
                    .dayOfWeek(dayOfWeek)
                    .sessionTime(sessionTime)
                    .eventType("PAYMENT_COMPLETED")
                    .eventTimestamp(eventTime)
                    .build();

            analyticsEventRepository.save(analyticsEvent);
            log.info("Evento de analítica persistido correctamente con ID: {}", analyticsEvent.getId());

        } catch (Exception ex) {
            log.error("Error al procesar el evento PaymentCompleted: {}", ex.getMessage(), ex);
        }
    }
}

