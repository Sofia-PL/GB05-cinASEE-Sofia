package es.cineasee.analitica.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analytics_events", indexes = {
        @Index(name = "idx_analytics_cinema", columnList = "cinemaId"),
        @Index(name = "idx_analytics_movie", columnList = "movieId"),
        @Index(name = "idx_analytics_timestamp", columnList = "eventTimestamp")
})
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    private Long userId;

    @Column(nullable = false)
    private String cinemaId;

    private String roomId;

    private String sessionId;

    private String movieId;

    private String movieTitle;

    private String genre;

    private String distributor;

    private Long distributorId;

    @Column(nullable = false)
    private Integer ticketsSold;

    @Column(nullable = false)
    private Double totalAmount;

    private Integer dayOfWeek; // 1 = Lunes, 7 = Domingo

    private String sessionTime; // e.g. "18:30"

    private String eventType; // e.g. "PAYMENT_COMPLETED"

    @Column(nullable = false)
    private LocalDateTime eventTimestamp;
}

