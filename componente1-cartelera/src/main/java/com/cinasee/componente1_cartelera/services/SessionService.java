package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateSessionRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.models.UpdateSessionRequest;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int PREPARATION_TIME_MINUTES = 20;

    private final SessionRepository sessionRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;

    public List<Session> getAllSessions(LocalDate date, String movieId) {
        List<Session> sessions = sessionRepository.findAll();

        if (date != null) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
            sessions = sessions.stream()
                    .filter(s -> s.getStartTime() != null
                            && !s.getStartTime().isBefore(startOfDay)
                            && !s.getStartTime().isAfter(endOfDay))
                    .collect(Collectors.toList());
        }

        if (movieId != null) {
            sessions = sessions.stream()
                    .filter(s -> movieId.equals(s.getMovieId()))
                    .collect(Collectors.toList());
        }

        return sessions;
    }

    public Optional<Session> getSessionById(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    public Session createSession(CreateSessionRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada con ID: " + request.getMovieId()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada con ID: " + request.getRoomId()));

        LocalDateTime startTime = request.getStartTime();
        int totalDuration = (movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 0) + PREPARATION_TIME_MINUTES;
        LocalDateTime endTime = startTime.plusMinutes(totalDuration);

        // Control de solapamiento horario en la misma sala
        boolean hasOverlap = checkRoomOverlap(room.getId(), startTime, endTime, null);
        if (hasOverlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solapamiento horario detectado. La sala seleccionada ya está ocupada en ese intervalo de tiempo (duración + tiempo de preparación).");
        }

        Session session = Session.builder()
                .movieId(movie.getId())
                .roomId(room.getId())
                .startTime(startTime)
                .endTime(endTime)
                .status("PROGRAMADA")
                .ticketPrice(request.getTicketPrice())
                .build();

        return sessionRepository.save(session);
    }

    public Session updateSessionStatus(String sessionId, UpdateSessionRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada con ID: " + sessionId));

        String targetRoomId = request.getRoomId() != null ? request.getRoomId() : session.getRoomId();
        LocalDateTime targetStartTime = request.getStartTime() != null ? request.getStartTime() : session.getStartTime();

        // Si cambia la sala o la hora de inicio, recalcular fin y validar solapamiento
        if (request.getRoomId() != null || request.getStartTime() != null) {
            if (request.getRoomId() != null && !roomRepository.existsById(targetRoomId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada con ID: " + targetRoomId);
            }

            Movie movie = movieRepository.findById(session.getMovieId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película de la sesión no encontrada"));

            int totalDuration = (movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 0) + PREPARATION_TIME_MINUTES;
            LocalDateTime targetEndTime = targetStartTime.plusMinutes(totalDuration);

            boolean hasOverlap = checkRoomOverlap(targetRoomId, targetStartTime, targetEndTime, sessionId);
            if (hasOverlap) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Conflicto de solapamiento con el nuevo horario o sala seleccionada.");
            }

            session.setRoomId(targetRoomId);
            session.setStartTime(targetStartTime);
            session.setEndTime(targetEndTime);
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            session.setStatus(request.getStatus().toUpperCase());
        }

        return sessionRepository.save(session);
    }

    private boolean checkRoomOverlap(String roomId, LocalDateTime start, LocalDateTime end, String excludeSessionId) {
        List<Session> roomSessions = sessionRepository.findByRoomIdAndStatusNot(roomId, "CANCELADA");

        return roomSessions.stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .anyMatch(s -> s.getStartTime() != null && s.getEndTime() != null
                        && s.getStartTime().isBefore(end)
                        && s.getEndTime().isAfter(start));
    }
}

