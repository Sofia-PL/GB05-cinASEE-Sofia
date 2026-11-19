package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "movies")
public class Movie {
    @Id
    private String id;
    private String title;
    private String synopsis;
    
    @Min(value = 1, message = "La duración debe ser mayor a 0") // Restricción añadida
    private Integer durationMinutes;
    
    private String genre;
    private String ageRating;
    private String posterUrl;
    private String trailerUrl;
}

