package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateMovieRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final SessionRepository sessionRepository;

    public List<Movie> getAllMovies(String genre, Boolean inShowcase) {
        List<Movie> movies;
        if (genre != null && !genre.isBlank()) {
            movies = movieRepository.findByGenreIgnoreCase(normalizarGenero(genre));
        } else {
            movies = movieRepository.findAll();
        }

        if (Boolean.TRUE.equals(inShowcase)) {
            LocalDateTime now = LocalDateTime.now();
            Set<String> activeMovieIds = sessionRepository.findAll().stream()
                    .filter(session -> !"CANCELADA".equalsIgnoreCase(session.getStatus()))
                    .filter(session -> session.getEndTime() != null && session.getEndTime().isAfter(now))
                    .map(Session::getMovieId)
                    .collect(Collectors.toSet());

            movies = movies.stream()
                    .filter(movie -> activeMovieIds.contains(movie.getId()))
                    .collect(Collectors.toList());
        }

        return movies;
    }

    public Optional<Movie> getMovieById(String movieId) {
        return movieRepository.findById(movieId);
    }

    public Movie createMovie(CreateMovieRequest request) {
        Movie movie = Movie.builder()
                .id(java.util.UUID.randomUUID().toString())
                .title(request.getTitle())
                .synopsis(request.getSynopsis())
                .durationMinutes(request.getDurationMinutes())
                .genre(normalizarGenero(request.getGenre()))
                .ageRating(normalizarEdad(request.getAgeRating()))
                .posterUrl(request.getPosterUrl())
                .trailerUrl(request.getTrailerUrl())
                .build();
        return movieRepository.save(movie);
    }

    public Movie updateMovie(String movieId, CreateMovieRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada con ID: " + movieId));

        movie.setTitle(request.getTitle());
        movie.setSynopsis(request.getSynopsis());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setGenre(normalizarGenero(request.getGenre()));
        movie.setAgeRating(normalizarEdad(request.getAgeRating()));
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerUrl(request.getTrailerUrl());

        return movieRepository.save(movie);
    }

    public void deleteMovie(String movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada con ID: " + movieId);
        }
        if (!sessionRepository.findByMovieId(movieId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la película porque tiene sesiones asociadas.");
        }
        movieRepository.deleteById(movieId);
    }

    /**
     * Género tal como lo guarda el catálogo: en mayúsculas, sin tildes y con "_" en vez de
     * espacios ("Ciencia ficción" -> "CIENCIA_FICCION"). Así coincide con los géneros favoritos
     * de los usuarios (Comp. 3) que usan las recomendaciones (Comp. 4).
     */
    public static String normalizarGenero(String genero) {
        if (genero == null) {
            return null;
        }
        String sinTildes = Normalizer.normalize(genero.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    /** Calificación por edad: "+0" (o "0") significa todos los públicos y se guarda como "TP" */
    public static String normalizarEdad(String edad) {
        if (edad == null) {
            return null;
        }
        String limpia = edad.trim().toUpperCase(Locale.ROOT);
        return "+0".equals(limpia) || "0".equals(limpia) ? "TP" : limpia;
    }
}

