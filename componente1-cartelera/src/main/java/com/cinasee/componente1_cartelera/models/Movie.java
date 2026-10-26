package es.cineasee.cartelera.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "movies")
public class Movie {
    @Id
    private Long id;
    private String title;
    private String synopsis;
    private Integer durationMinutes;
    private String genre;
    private String ageRating;
    private String distributor;
    private String posterUrl;
    private String trailerUrl;
}