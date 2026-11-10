package com.cinasee.componente1_cartelera;

import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CarteleraAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private MovieRepository movieRepository;
    @MockitoBean
    private RoomRepository roomRepository;
    @MockitoBean
    private SessionRepository sessionRepository;

    private Map<Long, Movie> movies;
    private Map<Long, Room> rooms;
    private Map<Long, Session> sessions;

    @BeforeEach
    void setUpRepositories() {
        movies = new HashMap<>();
        rooms = new HashMap<>();
        sessions = new HashMap<>();
        when(movieRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(movies.values()));
        when(movieRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(movies.get(invocation.getArgument(0))));
        when(movieRepository.existsById(anyLong())).thenAnswer(invocation -> movies.containsKey(invocation.getArgument(0)));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> {
            Movie movie = invocation.getArgument(0);
            movies.put(movie.getId(), movie);
            return movie;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            movies.remove(invocation.getArgument(0));
            return null;
        }).when(movieRepository).deleteById(anyLong());

        when(roomRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(rooms.values()));
        when(roomRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(rooms.get(invocation.getArgument(0))));
        when(roomRepository.existsById(anyLong())).thenAnswer(invocation -> rooms.containsKey(invocation.getArgument(0)));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            rooms.put(room.getId(), room);
            return room;
        });

        when(sessionRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(sessions.values()));
        when(sessionRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(sessionRepository.findByRoomIdAndStatusNot(anyLong(), any())).thenAnswer(invocation -> sessions.values().stream()
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
    void cinemaCanCreateCatalogAndScheduleWithoutRoomOverlap() throws Exception {
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
        long movieId = jsonMapper.readTree(movieResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult roomResult = mockMvc.perform(post("/api/v1/rooms").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sala de aceptación",
                                  "roomType": "ESTANDAR_2D",
                                  "rowsCount": 8,
                                  "columnsCount": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(80))
                .andReturn();
        long roomId = jsonMapper.readTree(roomResult.getResponse().getContentAsString()).get("id").asLong();

        LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/sessions").contextPath("/api/v1")
                        .with(jwt().jwt(token -> token.subject("acceptance-test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionRequest(movieId, roomId, start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROGRAMADA"))
                .andExpect(jsonPath("$.endTime").value(start.plusMinutes(120).toString() + ":00"))
                .andReturn();
        long sessionId = jsonMapper.readTree(sessionResult.getResponse().getContentAsString()).get("id").asLong();

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

    private static String sessionRequest(long movieId, long roomId, LocalDateTime start) {
        return """
                {
                  "movieId": %d,
                  "roomId": %d,
                  "startTime": "%s",
                  "ticketPrice": 8.5
                }
                """.formatted(movieId, roomId, start);
    }
}
