package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateMovieRequest;
import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.CreateSessionRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @InjectMocks
    private MovieService movieService;
    @InjectMocks
    private RoomService roomService;
    @InjectMocks
    private SessionService sessionService;

    @Test
    void movieFilterByGenreAndShowcaseOnlyReturnsMoviesWithUpcomingSessions() {
        Movie activeMovie = movie(1L);
        Movie inactiveMovie = movie(2L);
        when(movieRepository.findByGenreIgnoreCase("DRAMA")).thenReturn(List.of(activeMovie, inactiveMovie));
        when(sessionRepository.findAll()).thenReturn(List.of(
                session(10L, 1L, 7L, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "PROGRAMADA"),
                session(11L, 2L, 7L, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "CANCELADA"),
                session(12L, 2L, 7L, LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), "FINALIZADA")
        ));

        List<Movie> result = movieService.getAllMovies("DRAMA", true);

        assertEquals(List.of(activeMovie), result);
    }

    @Test
    void createRoomCalculatesCapacityAndUsesDefaults() {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .name("Sala 1")
                .roomType("ESTANDAR_2D")
                .rowsCount(8)
                .columnsCount(12)
                .build();
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room created = roomService.createRoom(request);

        assertEquals(96, created.getCapacity());
        assertFalse(created.getWheelchairAccessible());
        assertEquals(0.0, created.getBasePrice());
        verify(roomRepository).save(created);
    }

    @Test
    void createSessionAddsPreparationTimeToMovieDuration() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room(2L)));
        when(sessionRepository.findByRoomIdAndStatusNot(2L, "CANCELADA")).thenReturn(List.of());
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session created = sessionService.createSession(CreateSessionRequest.builder()
                .movieId(1L).roomId(2L).startTime(start).ticketPrice(8.5).build());

        assertEquals(start, created.getStartTime());
        assertEquals(start.plusMinutes(147), created.getEndTime());
        assertEquals("PROGRAMADA", created.getStatus());
    }

    @Test
    void createSessionRejectsOverlappingTimesInSameRoom() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room(2L)));
        when(sessionRepository.findByRoomIdAndStatusNot(2L, "CANCELADA")).thenReturn(List.of(
                session(5L, 8L, 2L, start.plusMinutes(100), start.plusMinutes(200), "A_LA_VENTA")
        ));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(CreateSessionRequest.builder()
                        .movieId(1L).roomId(2L).startTime(start).ticketPrice(8.5).build()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void createSessionAllowsAdjacentScreenings() {
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(18, 30);
        LocalDateTime end = start.plusMinutes(147);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room(2L)));
        when(sessionRepository.findByRoomIdAndStatusNot(2L, "CANCELADA")).thenReturn(List.of(
                session(5L, 8L, 2L, end, end.plusHours(2), "A_LA_VENTA")
        ));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session created = sessionService.createSession(CreateSessionRequest.builder()
                .movieId(1L).roomId(2L).startTime(start).ticketPrice(8.5).build());

        assertEquals(start, created.getStartTime());
    }

    @Test
    void createSessionReturnsNotFoundWhenMovieDoesNotExist() {
        when(movieRepository.findById(anyLong())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                sessionService.createSession(CreateSessionRequest.builder()
                        .movieId(99L).roomId(2L).startTime(LocalDateTime.now()).ticketPrice(8.5).build()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertTrue(error.getReason().contains("Película no encontrada"));
        verify(roomRepository, never()).findById(anyLong());
    }

    private static Movie movie(Long id) {
        return Movie.builder().id(id).title("Película " + id).durationMinutes(127).genre("DRAMA").build();
    }

    private static Room room(Long id) {
        return Room.builder().id(id).name("Sala " + id).build();
    }

    private static Session session(Long id, Long movieId, Long roomId, LocalDateTime start,
                                   LocalDateTime end, String status) {
        return Session.builder().id(id).movieId(movieId).roomId(roomId).startTime(start).endTime(end).status(status).build();
    }
}
