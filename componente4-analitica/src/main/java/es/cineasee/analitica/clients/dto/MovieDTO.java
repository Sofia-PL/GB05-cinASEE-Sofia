package es.cineasee.analitica.clients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDTO {
    private String id;
    private String title;
    private String synopsis;
    private Integer durationMinutes;
    private String genre;
    private String ageRating;
    private String distributor;
    private String posterUrl;
    private String trailerUrl;
}

