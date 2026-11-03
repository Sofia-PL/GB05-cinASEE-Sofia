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

import java.time.LocalDateTime;
import java.util.List;
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
            movies = movieRepository.findByGenreIgnoreCase(genre);
        } else {
            movies = movieRepository.findAll();
        }

        if (Boolean.TRUE.equals(inShowcase)) {
            LocalDateTime now = LocalDateTime.now();
            Set<Long> activeMovieIds = sessionRepository.findAll().stream()
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

    public Optional<Movie> getMovieById(Long movieId) {
        return movieRepository.findById(movieId);
    }

    public Movie createMovie(CreateMovieRequest request) {
        Long nextId = System.currentTimeMillis();
        Movie movie = Movie.builder()
                .id(nextId)
                .title(request.getTitle())
                .synopsis(request.getSynopsis())
                .durationMinutes(request.getDurationMinutes())
                .genre(request.getGenre())
                .ageRating(request.getAgeRating())
                .distributor(request.getDistributor())
                .posterUrl(request.getPosterUrl())
                .trailerUrl(request.getTrailerUrl())
                .build();
        return movieRepository.save(movie);
    }

    public Movie updateMovie(Long movieId, CreateMovieRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada con ID: " + movieId));

        movie.setTitle(request.getTitle());
        movie.setSynopsis(request.getSynopsis());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setGenre(request.getGenre());
        movie.setAgeRating(request.getAgeRating());
        movie.setDistributor(request.getDistributor());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerUrl(request.getTrailerUrl());

        return movieRepository.save(movie);
    }

    public void deleteMovie(Long movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada con ID: " + movieId);
        }
        movieRepository.deleteById(movieId);
    }
}

