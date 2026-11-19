package es.cineasee.analitica.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent implements Serializable {
    private Long reservationId;
    private Long userId;
    private String cinemaId;
    private String roomId;
    private String sessionId;
    private String movieId;
    private String movieTitle;
    private String genre;
    private String distributor;
    private Long distributorId;
    private Integer ticketsCount;
    private Double totalAmount;
    private LocalDateTime timestamp;
}

