package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.Cinema;
import com.cinasee.componente1_cartelera.models.CreateMovieRequest;
import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.CreateSessionRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.models.UpdateSessionRequest;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServicesTest {

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private MovieService movieService;
    @InjectMocks
    private RoomService roomService;
    @InjectMocks
    private SessionService sessionService;

    @Test
    void movieFilterByGenreAndShowcaseOnlyReturnsMoviesWithUpcomingSessions() {
        Movie activeMovie = movie("movie-1");
        Movie inactiveMovie = movie("movie-2");
        when(movieRepository.findByGenreIgnoreCase("DRAMA")).thenReturn(List.of(activeMovie, inactiveMovie));
        when(sessionRepository.findAll()).thenReturn(List.of(
                session("session-10", "movie-1", "room-7", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "PROGRAMADA"),
                session("session-11", "movie-2", "room-7", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "CANCELADA"),
                session("session-12", "movie-2", "room-7", LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), "FINALIZADA")
        ));

        List<Movie> result = movieService.getAllMovies("DRAMA", true);

        assertEquals(List.of(activeMovie), result);
    }

    @Test
    void createRoomCalculatesCapacityAndUsesDefaults() {
        when(cinemaRepository.existsById("cinema-3")).thenReturn(true);
        CreateRoomRequest request = CreateRoomRequest.builder()
                .cinemaId("cinema-3")
                .name("Sala 1")
                .roomType("ESTANDAR_2D")
                .rowsCount(8)
                .columnsCount(12)
                .build();
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room created = roomService.createRoom(request);

        assertEquals(96, created.getCapacity());
        assertEquals(UUID.fromString(created.getId()).toString(), created.getId());
        assertEquals("cinema-3", created.getCinemaId());
        assertFalse(created.getWheelchairAccessible());
        assertEquals(0.0, created.getBasePrice());
        verify(cinemaRepository).existsById("cinema-3");
        verify(roomRepository).save(created);
    }

    @Test
    void createRoomRejectsUnknownCinema() {
        when(cinemaRepository.existsById("unknown-cinema")).thenReturn(false);
        CreateRoomRequest request = CreateRoomRequest.builder()
                .cinemaId("unknown-cinema")
                .name("Sala 1")
                .roomType("ESTANDAR_2D")
                .rowsCount(8)
                .columnsCount(12)
                .build();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> roomService.createRoom(request));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertTrue(error.getReason().contains("Cine no encontrado"));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void createSessionAddsPreparationTimeToMovieDuration() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(movie("movie-1")));
        when(roomRepository.findById("room-2")).thenReturn(Optional.of(room("room-2")));
        when(sessionRepository.findByRoomIdAndStatusNot("room-2", "CANCELADA")).thenReturn(List.of());
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session created = sessionService.createSession(CreateSessionRequest.builder()
                .movieId("movie-1").roomId("room-2").startTime(start).ticketPrice(8.5).build());

        assertEquals(start, created.getStartTime());
        assertEquals(start.plusMinutes(147), created.getEndTime());
        assertEquals("PROGRAMADA", created.getStatus());
        assertTrue(created.getId().matches("[0-7][0-9A-HJKMNP-TV-Z]{25}"));
    }

    @Test
    void createSessionRejectsOverlappingTimesInSameRoom() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(movie("movie-1")));
        when(roomRepository.findById("room-2")).thenReturn(Optional.of(room("room-2")));
        when(sessionRepository.findByRoomIdAndStatusNot("room-2", "CANCELADA")).thenReturn(List.of(
                session("session-5", "movie-8", "room-2", start.plusMinutes(100), start.plusMinutes(200), "A_LA_VENTA")
        ));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(CreateSessionRequest.builder()
                        .movieId("movie-1").roomId("room-2").startTime(start).ticketPrice(8.5).build()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void createSessionAllowsAdjacentScreenings() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        LocalDateTime end = start.plusMinutes(147);
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(movie("movie-1")));
        when(roomRepository.findById("room-2")).thenReturn(Optional.of(room("room-2")));
        when(sessionRepository.findByRoomIdAndStatusNot("room-2", "CANCELADA")).thenReturn(List.of(
                session("session-5", "movie-8", "room-2", end, end.plusHours(2), "A_LA_VENTA")
        ));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session created = sessionService.createSession(CreateSessionRequest.builder()
                .movieId("movie-1").roomId("room-2").startTime(start).ticketPrice(8.5).build());

        assertEquals(start, created.getStartTime());
    }

    @Test
    void createSessionReturnsNotFoundWhenMovieDoesNotExist() {
        when(movieRepository.findById(anyString())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(CreateSessionRequest.builder()
                        .movieId("missing-movie").roomId("room-2").startTime(LocalDateTime.now()).ticketPrice(8.5).build()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertTrue(error.getReason().contains("Película no encontrada"));
        verify(roomRepository, never()).findById(anyString());
    }

    @Test
    void updateSessionChangesTicketPriceWithoutTouchingSchedule() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 20, 0);
        Session existing = session("s1", "m1", "room-2", start, start.plusMinutes(147), "A_LA_VENTA");
        existing.setTicketPrice(9.5);
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session updated = sessionService.updateSessionStatus("s1", UpdateSessionRequest.builder().ticketPrice(11.0).build());

        assertEquals(11.0, updated.getTicketPrice());
        assertEquals(start, updated.getStartTime());
        assertEquals("room-2", updated.getRoomId());
        assertEquals("A_LA_VENTA", updated.getStatus());
        verify(movieRepository, never()).findById(anyString());
    }

    @Test
    void updateSessionWithoutPriceKeepsCurrentPrice() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 20, 0);
        Session existing = session("s1", "m1", "room-2", start, start.plusMinutes(147), "PROGRAMADA");
        existing.setTicketPrice(9.5);
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session updated = sessionService.updateSessionStatus("s1", UpdateSessionRequest.builder().status("a_la_venta").build());

        assertEquals(9.5, updated.getTicketPrice());
        assertEquals("A_LA_VENTA", updated.getStatus());
    }

    // ---------- Géneros y edades normalizados (puntos 4 y 5) ----------

    @Test
    void normalizarGeneroQuitaTildesPasaAMayusculasYUsaGuionBajo() {
        assertEquals("CIENCIA_FICCION", MovieService.normalizarGenero("Ciencia ficción"));
        assertEquals("ANIMACION", MovieService.normalizarGenero("ANIMACIÓN"));
        assertEquals("FANTASIA", MovieService.normalizarGenero(" fantasía "));
        assertEquals("CIENCIA_FICCION", MovieService.normalizarGenero("CIENCIA_FICCION"));
        assertEquals(null, MovieService.normalizarGenero(null));
    }

    @Test
    void normalizarEdadConvierteMasCeroEnTodosLosPublicos() {
        assertEquals("TP", MovieService.normalizarEdad("+0"));
        assertEquals("TP", MovieService.normalizarEdad("tp"));
        assertEquals("+12", MovieService.normalizarEdad(" +12 "));
    }

    @Test
    void crearYEditarPeliculaGuardaGeneroYEdadNormalizados() {
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Ponyo").durationMinutes(101).genre("Animación").ageRating("+0").build();

        Movie creada = movieService.createMovie(request);
        assertEquals("ANIMACION", creada.getGenre());
        assertEquals("TP", creada.getAgeRating());

        when(movieRepository.findById("m1")).thenReturn(Optional.of(movie("m1")));
        Movie editada = movieService.updateMovie("m1", CreateMovieRequest.builder()
                .title("Ponyo").durationMinutes(101).genre("ciencia ficción").ageRating("+7").build());
        assertEquals("CIENCIA_FICCION", editada.getGenre());
        assertEquals("+7", editada.getAgeRating());
    }

    // ---------- Cambio automático de estado de las sesiones (punto 7) ----------

    @Test
    void sesionQueEmpiezaPasaAEnCursoYLaQueTerminaAFinalizada() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 19, 0);
        Session empezando = session("s1", "m1", "room-1", ahora.minusMinutes(5), ahora.plusHours(2), "A_LA_VENTA");
        Session terminando = session("s2", "m1", "room-2", ahora.minusHours(3), ahora.minusMinutes(1), "EN_CURSO");
        when(sessionRepository.findByStatusInAndStartTimeLessThanEqual(List.of("PROGRAMADA", "A_LA_VENTA"), ahora))
                .thenReturn(List.of(empezando));
        when(sessionRepository.findByStatusAndEndTimeLessThanEqual("EN_CURSO", ahora)).thenReturn(List.of(terminando));

        int cambiadas = sessionService.actualizarEstadosPorHora(ahora);

        assertEquals(2, cambiadas);
        assertEquals("EN_CURSO", empezando.getStatus());
        assertEquals("FINALIZADA", terminando.getStatus());
        verify(sessionRepository).save(empezando);
        verify(sessionRepository).save(terminando);
    }

    @Test
    void sesionQueYaHaTerminadoSinEmpezarPasaDirectamenteAFinalizada() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 23, 0);
        Session pasada = session("s1", "m1", "room-1", ahora.minusHours(4), ahora.minusHours(2), "PROGRAMADA");
        when(sessionRepository.findByStatusInAndStartTimeLessThanEqual(List.of("PROGRAMADA", "A_LA_VENTA"), ahora))
                .thenReturn(List.of(pasada));
        when(sessionRepository.findByStatusAndEndTimeLessThanEqual("EN_CURSO", ahora)).thenReturn(List.of());

        sessionService.actualizarEstadosPorHora(ahora);

        assertEquals("FINALIZADA", pasada.getStatus());
    }

    @Test
    void elCambioAutomaticoSoloBuscaSesionesProgramadasALaVentaOEnCurso() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 19, 0);
        when(sessionRepository.findByStatusInAndStartTimeLessThanEqual(any(), any())).thenReturn(List.of());
        when(sessionRepository.findByStatusAndEndTimeLessThanEqual(anyString(), any())).thenReturn(List.of());

        assertEquals(0, sessionService.actualizarEstadosPorHora(ahora));

        // CANCELADA y FINALIZADA nunca se consultan, así que nunca se modifican
        verify(sessionRepository).findByStatusInAndStartTimeLessThanEqual(List.of("PROGRAMADA", "A_LA_VENTA"), ahora);
        verify(sessionRepository).findByStatusAndEndTimeLessThanEqual("EN_CURSO", ahora);
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void inicioManualRecalculaElFinSegunLaDuracionDesdeAhora() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 18, 30);
        Clock relojFijo = Clock.fixed(ahora.atZone(ZoneId.of("Europe/Madrid")).toInstant(), ZoneId.of("Europe/Madrid"));
        SessionService servicio = new SessionService(sessionRepository, movieRepository, roomRepository, relojFijo);
        LocalDateTime inicioPrevisto = LocalDateTime.of(2026, 11, 20, 19, 0);
        Session sesion = session("s1", "m1", "room-1", inicioPrevisto, inicioPrevisto.plusMinutes(147), "A_LA_VENTA");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(sesion));
        when(movieRepository.findById("m1")).thenReturn(Optional.of(movie("m1")));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session actualizada = servicio.updateSessionStatus("s1", UpdateSessionRequest.builder().status("EN_CURSO").build());

        assertEquals("EN_CURSO", actualizada.getStatus());
        // 127 min de película + 20 de preparación, contados desde que se inició a mano
        assertEquals(ahora.plusMinutes(147), actualizada.getEndTime());
        assertEquals(inicioPrevisto, actualizada.getStartTime());
    }

    private static Movie movie(String id) {
        return Movie.builder().id(id).title("Película " + id).durationMinutes(127).genre("DRAMA").build();
    }

    private static Room room(String id) {
        return Room.builder().id(id).cinemaId("cinema-3").name("Sala " + id).build();
    }

    private static Session session(String id, String movieId, String roomId, LocalDateTime start,
                                   LocalDateTime end, String status) {
        return Session.builder().id(id).movieId(movieId).roomId(roomId).startTime(start).endTime(end).status(status).build();
    }
}
