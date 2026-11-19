package com.cinasee.componente1_cartelera.models;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sessions")
public class Session {
    @Id
    @Builder.Default
    private String id = UlidCreator.getMonotonicUlid().toString();
    private String movieId;
    private String roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // PROGRAMADA, A_LA_VENTA, EN_CURSO, FINALIZADA, CANCELADA
    
    @Min(0) // Precio no negativo
    private Double ticketPrice;
}

