package com.cinasee.componente1_cartelera.controllers;

import com.cinasee.componente1_cartelera.config.SecurityConfig;
import com.cinasee.componente1_cartelera.models.CreateMovieRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.services.MovieService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieController.class)
@Import(SecurityConfig.class)
class MovieControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    @Test
    void listMoviesIsPublicAndReturnsJson() throws Exception {
        when(movieService.getAllMovies(null, null)).thenReturn(List.of(
                Movie.builder().id("movie-1").title("Orgullo y prejuicio").durationMinutes(127).genre("DRAMA").build()
        ));

        mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$[0].id").value("movie-1"))
                .andExpect(jsonPath("$[0].title").value("Orgullo y prejuicio"));
    }

    @Test
    void publicEndpointReachesServiceWhenDatabaseFails() {
        when(movieService.getAllMovies(null, null))
                .thenThrow(new DataAccessResourceFailureException("MongoDB unavailable"));

        ServletException error = assertThrows(ServletException.class, () ->
                mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1")));

        assertInstanceOf(DataAccessResourceFailureException.class, error.getCause());
        verify(movieService).getAllMovies(null, null);
    }

    @Test
    void movieDetailReturnsNotFoundWhenServiceHasNoMovie() throws Exception {
        when(movieService.getMovieById("404")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/movies/404").contextPath("/api/v1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingMovieRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/movies").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCreateValidatesRequestAndCreatesMovie() throws Exception {
        Movie created = Movie.builder().id("movie-15").title("Nueva película").durationMinutes(90).genre("DRAMA").build();
        when(movieService.createMovie(any(CreateMovieRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/movies").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("integration-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","durationMinutes":0,"genre":"","ageRating":"TP"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/movies").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("integration-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("movie-15"))
                .andExpect(jsonPath("$.title").value("Nueva película"));

        verify(movieService).createMovie(any(CreateMovieRequest.class));
    }

    private static String validMovieJson() {
        return """
                {
                  "title": "Orgullo y prejuicio",
                  "synopsis": "Drama romántico",
                  "durationMinutes": 127,
                  "genre": "DRAMA_ROMANTICO",
                  "ageRating": "TP"
                }
                """;
    }
}
