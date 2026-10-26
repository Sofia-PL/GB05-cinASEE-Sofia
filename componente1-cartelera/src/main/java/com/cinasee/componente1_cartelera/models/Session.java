package es.cineasee.cartelera.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "sessions")
public class Session {
    @Id
    private Long id;
    private Long movieId;
    private Long roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // PROGRAMADA, A_LA_VENTA, EN_CURSO, FINALIZADA, CANCELADA
    private Double ticketPrice;
}