package com.cinasee.componente1_cartelera;

import com.cinasee.componente1_cartelera.models.Cinema;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.seed.enabled=false", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
class CarteleraAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private MovieRepository movieRepository;
    @MockitoBean
    private CinemaRepository cinemaRepository;
    @MockitoBean
    private RoomRepository roomRepository;
    @MockitoBean
    private SessionRepository sessionRepository;

    private Map<String, Cinema> cinemas;
    private Map<String, Movie> movies;
    private Map<String, Room> rooms;
    private Map<String, Session> sessions;

    @BeforeEach
    void setUpRepositories() {
        cinemas = new HashMap<>();
        movies = new HashMap<>();
        rooms = new HashMap<>();
        sessions = new HashMap<>();
        when(cinemaRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(cinemas.values()));
        when(cinemaRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(cinemas.get(invocation.getArgument(0))));
        when(cinemaRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> cinemas.containsKey(invocation.getArgument(0)));
        when(cinemaRepository.save(any(Cinema.class))).thenAnswer(invocation -> {
            Cinema cinema = invocation.getArgument(0);
            cinemas.put(cinema.getId(), cinema);
            return cinema;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            cinemas.remove(invocation.getArgument(0));
            return null;
        }).when(cinemaRepository).deleteById(org.mockito.ArgumentMatchers.anyString());
        when(movieRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(movies.values()));
        when(movieRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(movies.get(invocation.getArgument(0))));
        when(movieRepository.findByGenreIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String genre = invocation.getArgument(0);
            return movies.values().stream()
                    .filter(movie -> genre.equalsIgnoreCase(movie.getGenre()))
                    .toList();
        });
        when(movieRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> movies.containsKey(invocation.getArgument(0)));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> {
            Movie movie = invocation.getArgument(0);
            movies.put(movie.getId(), movie);
            return movie;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            movies.remove(invocation.getArgument(0));
            return null;
        }).when(movieRepository).deleteById(org.mockito.ArgumentMatchers.anyString());

        when(roomRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(rooms.values()));
        when(roomRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(rooms.get(invocation.getArgument(0))));
        when(roomRepository.findByCinemaId(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> rooms.values().stream()
                .filter(room -> invocation.getArgument(0).equals(room.getCinemaId()))
                .toList());
        when(roomRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> rooms.containsKey(invocation.getArgument(0)));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            rooms.put(room.getId(), room);
            return room;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            rooms.remove(invocation.getArgument(0));
            return null;
        }).when(roomRepository).deleteById(org.mockito.ArgumentMatchers.anyString());

        when(sessionRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(sessions.values()));
        when(sessionRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(sessionRepository.findByMovieId(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> sessions.values().stream()
                .filter(session -> invocation.getArgument(0).equals(session.getMovieId()))
                .toList());
        when(sessionRepository.findByRoomIdAndStatusNot(org.mockito.ArgumentMatchers.anyString(), any())).thenAnswer(invocation -> sessions.values().stream()
                .filter(session -> invocation.getArgument(0).equals(session.getRoomId()))
                .filter(session -> !invocation.getArgument(1).equals(session.getStatus()))
                .toList());
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            sessions.put(session.getId(), session);
            return session;
        });
    }

    @Test
    void roomCanBeUpdatedAndCapacityIsRecalculated() throws Exception {
        cinemas.put("cinema-1", Cinema.builder().id("cinema-1").build());
        rooms.put("room-1", Room.builder()
                .id("room-1")
                .cinemaId("cinema-1")
                .name("Sala antigua")
                .roomType("ESTANDAR_2D")
                .rowsCount(8)
                .columnsCount(10)
                .capacity(80)
                .build());

        mockMvc.perform(put("/api/v1/rooms/room-1").contextPath("/api/v1")
                        .with(tokenWithRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomRequest("cinema-1", "Sala renovada", 12, 14)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sala renovada"))
                .andExpect(jsonPath("$.capacity").value(168))
                .andExpect(jsonPath("$.rowsCount").value(12))
                .andExpect(jsonPath("$.columnsCount").value(14));
    }

    @Test
    void updatingUnknownRoomReturnsNotFound() throws Exception {
        cinemas.put("cinema-1", Cinema.builder().id("cinema-1").build());

        mockMvc.perform(put("/api/v1/rooms/missing").contextPath("/api/v1")
                        .with(tokenWithRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomRequest("cinema-1", "Sala nueva", 10, 12)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomWithFutureSessionCannotBeDeleted() throws Exception {
        rooms.put("room-1", Room.builder().id("room-1").build());
        sessions.put("session-1", Session.builder()
                .id("session-1")
                .roomId("room-1")
                .startTime(LocalDateTime.now().plusDays(1))
                .status("PROGRAMADA")
                .build());

        mockMvc.perform(delete("/api/v1/rooms/room-1").contextPath("/api/v1")
                        .with(tokenWithRole("ADMIN")))
                .andExpect(status().isConflict());
        assertTrue(rooms.containsKey("room-1"));
    }

    @Test
    void roomWithoutFutureSessionsCanBeDeleted() throws Exception {
        rooms.put("room-1", Room.builder().id("room-1").build());
        sessions.put("session-1", Session.builder()
                .id("session-1")
                .roomId("room-1")
                .startTime(LocalDateTime.now().plusDays(1))
                .status("CANCELADA")
                .build());

        mockMvc.perform(delete("/api/v1/rooms/room-1").contextPath("/api/v1")
                        .with(tokenWithRole("ADMIN")))
                .andExpect(status().isNoContent());
        assertFalse(rooms.containsKey("room-1"));
    }

    @Test
    void nonAdminCannotUpdateOrDeleteRooms() throws Exception {
        cinemas.put("cinema-1", Cinema.builder().id("cinema-1").build());
        rooms.put("room-1", Room.builder()
                .id("room-1")
                .cinemaId("cinema-1")
                .name("Sala original")
                .build());

        mockMvc.perform(put("/api/v1/rooms/room-1").contextPath("/api/v1")
                        .with(tokenWithRole("CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomRequest("cinema-1", "Sala modificada", 12, 14)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/rooms/room-1").contextPath("/api/v1")
                        .with(tokenWithRole("CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());
        assertTrue(rooms.containsKey("room-1"));
    }

    @Test
    void batchCreatesAvailableDatesAndReturnsScheduleConflicts() throws Exception {
        movies.put("movie-1", Movie.builder().id("movie-1").durationMinutes(100).build());
        rooms.put("room-1", Room.builder().id("room-1").build());
        sessions.put("existing-session", Session.builder()
                .id("existing-session")
                .movieId("movie-1")
                .roomId("room-1")
                .startTime(LocalDate.of(2026, 11, 23).atTime(17, 0))
                .endTime(LocalDate.of(2026, 11, 23).atTime(20, 0))
                .status("PROGRAMADA")
                .build());

        mockMvc.perform(post("/api/v1/sessions/batch").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": "movie-1",
                                  "roomId": "room-1",
                                  "daysOfWeek": ["MONDAY", "WEDNESDAY"],
                                  "startDate": "2026-11-23",
                                  "endDate": "2026-11-25",
                                  "startTime": "18:00",
                                  "ticketPrice": 9.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created.length()").value(1))
                .andExpect(jsonPath("$.created[0].startTime").value("2026-11-25T18:00:00"))
                .andExpect(jsonPath("$.created[0].endTime").value("2026-11-25T20:00:00"))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].startTime").value("2026-11-23T18:00:00"))
                .andExpect(jsonPath("$.conflicts[0].conflictingSessionId").value("existing-session"));
    }

    @Test
    void batchRejectsAnInvertedDateRange() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/batch").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": "movie-1",
                                  "roomId": "room-1",
                                  "daysOfWeek": ["MONDAY"],
                                  "startDate": "2026-11-25",
                                  "endDate": "2026-11-23",
                                  "startTime": "18:00",
                                  "ticketPrice": 9.5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movieWithAnySessionCannotBeDeleted() throws Exception {
        movies.put("movie-1", Movie.builder().id("movie-1").build());
        sessions.put("session-1", Session.builder()
                .id("session-1")
                .movieId("movie-1")
                .startTime(LocalDateTime.now().minusDays(1))
                .status("CANCELADA")
                .build());

        mockMvc.perform(delete("/api/v1/movies/movie-1").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test"))))
                .andExpect(status().isConflict());
        assertTrue(movies.containsKey("movie-1"));
    }

    @Test
    void cinemaWithAnyRoomCannotBeDeleted() throws Exception {
        cinemas.put("cinema-1", Cinema.builder().id("cinema-1").build());
        rooms.put("room-1", Room.builder().id("room-1").cinemaId("cinema-1").build());

        mockMvc.perform(delete("/api/v1/cinemas/cinema-1").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test"))))
                .andExpect(status().isConflict());
        assertTrue(cinemas.containsKey("cinema-1"));
    }

    @Test
    void cinemaCanCreateCatalogAndScheduleWithoutRoomOverlap() throws Exception {
        MvcResult cinemaResult = mockMvc.perform(post("/api/v1/cinemas").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cine de aceptación",
                                  "address": "Calle de prueba 1",
                                  "city": "Madrid",
                                  "phone": "910000000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cine de aceptación"))
                .andReturn();
        String cinemaId = jsonMapper.readTree(cinemaResult.getResponse().getContentAsString()).get("id").asText();
        assertEquals(UUID.fromString(cinemaId).toString(), cinemaId);

        MvcResult movieResult = mockMvc.perform(post("/api/v1/movies").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Película de prueba",
                                  "durationMinutes": 100,
                                  "genre": "DRAMA",
                                  "ageRating": "TP"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Película de prueba"))
                .andReturn();
        String movieId = jsonMapper.readTree(movieResult.getResponse().getContentAsString()).get("id").asText();
        assertEquals(UUID.fromString(movieId).toString(), movieId);

        MvcResult roomResult = mockMvc.perform(post("/api/v1/rooms").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cinemaId": "%s",
                                  "name": "Sala de aceptación",
                                  "roomType": "ESTANDAR_2D",
                                  "rowsCount": 8,
                                  "columnsCount": 10
                                }
                                """.formatted(cinemaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cinemaId").value(cinemaId))
                .andExpect(jsonPath("$.capacity").value(80))
                .andReturn();
        String roomId = jsonMapper.readTree(roomResult.getResponse().getContentAsString()).get("id").asText();
        assertEquals(UUID.fromString(roomId).toString(), roomId);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/sessions").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionRequest(movieId, roomId, start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROGRAMADA"))
                .andExpect(jsonPath("$.endTime").value(start.plusMinutes(120).toString() + ":00"))
                .andReturn();
        String sessionId = jsonMapper.readTree(sessionResult.getResponse().getContentAsString()).get("id").asText();
        assertTrue(sessionId.matches("[0-7][0-9A-HJKMNP-TV-Z]{25}"));

        mockMvc.perform(post("/api/v1/sessions").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionRequest(movieId, roomId, start.plusMinutes(100))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/sessions").contextPath("/api/v1")
                        .param("movieId", String.valueOf(movieId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId));

        mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(movieId));
    }

    private static String sessionRequest(String movieId, String roomId, LocalDateTime start) {
        return """
                {
                  "movieId": "%s",
                  "roomId": "%s",
                  "startTime": "%s",
                  "ticketPrice": 8.5
                }
                """.formatted(movieId, roomId, start);
    }

    private static String roomRequest(String cinemaId, String name, int rows, int columns) {
        return """
                {
                  "cinemaId": "%s",
                  "name": "%s",
                  "roomType": "ESTANDAR_2D",
                  "rowsCount": %d,
                  "columnsCount": %d
                }
                """.formatted(cinemaId, name, rows, columns);
    }

    private static RequestPostProcessor tokenWithRole(String role) {
        return jwt().jwt(token -> token.subject("acceptance-test").claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
