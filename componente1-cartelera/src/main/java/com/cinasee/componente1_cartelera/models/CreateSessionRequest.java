package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "El ID de la película es obligatorio")
    private String movieId;

    @NotNull(message = "El ID de la sala es obligatorio")
    private String roomId;

    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    private LocalDateTime startTime;

    @NotNull(message = "El precio de la entrada es obligatorio")
    @Min(value = 0, message = "El precio de la entrada no puede ser negativo")
    private Double ticketPrice;
}

