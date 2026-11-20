package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionBatchRequest {

    @NotNull(message = "El ID de la película es obligatorio")
    private String movieId;

    @NotNull(message = "El ID de la sala es obligatorio")
    private String roomId;

    @NotEmpty(message = "Debe indicarse al menos un día de la semana")
    private List<@NotNull DayOfWeek> daysOfWeek;

    @NotNull(message = "La fecha inicial es obligatoria")
    private LocalDate startDate;

    @NotNull(message = "La fecha final es obligatoria")
    private LocalDate endDate;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime startTime;

    @NotNull(message = "El precio de la entrada es obligatorio")
    @Min(value = 0, message = "El precio de la entrada no puede ser negativo")
    private Double ticketPrice;
}
