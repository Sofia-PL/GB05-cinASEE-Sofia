package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMovieRequest {

    @NotBlank(message = "El título de la película es obligatorio")
    private String title;

    private String synopsis;

    @NotNull(message = "La duración en minutos es obligatoria")
    @Min(value = 1, message = "La duración debe ser mayor a 0")
    private Integer durationMinutes;

    @NotBlank(message = "El género cinematográfico es obligatorio")
    private String genre;

    @NotBlank(message = "La clasificación por edades es obligatoria")
    private String ageRating;

    private String posterUrl;

    private String trailerUrl;
}

