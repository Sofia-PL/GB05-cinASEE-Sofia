package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateSessionRequest;
import com.cinasee.componente1_cartelera.models.CreateSessionBatchRequest;
import com.cinasee.componente1_cartelera.models.Movie;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.models.SessionBatchResponse;
import com.cinasee.componente1_cartelera.models.UpdateSessionRequest;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int PREPARATION_TIME_MINUTES = 20;

    /** Estados que pasan a EN_CURSO al llegar la hora de inicio */
    private static final List<String> ESTADOS_ANTES_DE_EMPEZAR = List.of("PROGRAMADA", "A_LA_VENTA");

    private final SessionRepository sessionRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    /** Hora local del cine (ver CatalogTimeConfig) */
    private final Clock catalogClock;

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
        if (findRoomOverlap(room.getId(), startTime, endTime, null).isPresent()) {
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

    public SessionBatchResponse createSessionsBatch(CreateSessionBatchRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Película no encontrada con ID: " + request.getMovieId()));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sala no encontrada con ID: " + request.getRoomId()));

        int totalDuration = (movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 0)
                + PREPARATION_TIME_MINUTES;
        List<Session> created = new ArrayList<>();
        List<SessionBatchResponse.Conflict> conflicts = new ArrayList<>();

        for (LocalDate date = request.getStartDate();
             !date.isAfter(request.getEndDate());
             date = date.plusDays(1)) {
            if (!request.getDaysOfWeek().contains(date.getDayOfWeek())) {
                continue;
            }

            LocalDateTime startTime = date.atTime(request.getStartTime());
            LocalDateTime endTime = startTime.plusMinutes(totalDuration);
            Optional<Session> conflictingSession = findRoomOverlap(room.getId(), startTime, endTime, null);
            if (conflictingSession.isPresent()) {
                conflicts.add(new SessionBatchResponse.Conflict(
                        startTime, endTime, conflictingSession.get().getId()));
                continue;
            }

            Session session = Session.builder()
                    .movieId(movie.getId())
                    .roomId(room.getId())
                    .startTime(startTime)
                    .endTime(endTime)
                    .status("PROGRAMADA")
                    .ticketPrice(request.getTicketPrice())
                    .build();
            created.add(sessionRepository.save(session));
        }

        return new SessionBatchResponse(created, conflicts);
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

            if (findRoomOverlap(targetRoomId, targetStartTime, targetEndTime, sessionId).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Conflicto de solapamiento con el nuevo horario o sala seleccionada.");
            }

            session.setRoomId(targetRoomId);
            session.setStartTime(targetStartTime);
            session.setEndTime(targetEndTime);
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String nuevoEstado = request.getStatus().toUpperCase();
            boolean inicioManual = "EN_CURSO".equals(nuevoEstado) && !"EN_CURSO".equals(session.getStatus());
            session.setStatus(nuevoEstado);

            // Inicio manual: la sesión termina cuando acabe la película contando desde ahora
            // (duración + preparación de sala), y la tarea periódica la finaliza a esa hora.
            if (inicioManual) {
                Movie movie = movieRepository.findById(session.getMovieId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película de la sesión no encontrada"));
                int totalDuration = (movie.getDurationMinutes() != null ? movie.getDurationMinutes() : 0) + PREPARATION_TIME_MINUTES;
                session.setEndTime(LocalDateTime.now(catalogClock).plusMinutes(totalDuration));
            }
        }

        if (request.getTicketPrice() != null) {
            session.setTicketPrice(request.getTicketPrice());
        }

        return sessionRepository.save(session);
    }

    /**
     * Cambia el estado de las sesiones según la hora:
     * <ul>
     *   <li>PROGRAMADA o A_LA_VENTA cuya hora de inicio ya ha llegado: EN_CURSO
     *       (o FINALIZADA directamente si también ha pasado su hora de fin, por ejemplo
     *       porque el servicio estuvo parado).</li>
     *   <li>EN_CURSO cuya hora de fin ya ha llegado: FINALIZADA.</li>
     * </ul>
     * Las sesiones CANCELADA y FINALIZADA no se tocan.
     *
     * @param ahora hora local del cine
     * @return número de sesiones cambiadas
     */
    public int actualizarEstadosPorHora(LocalDateTime ahora) {
        int cambiadas = 0;
        for (Session session : sessionRepository.findByStatusInAndStartTimeLessThanEqual(ESTADOS_ANTES_DE_EMPEZAR, ahora)) {
            boolean yaTerminada = session.getEndTime() != null && !session.getEndTime().isAfter(ahora);
            session.setStatus(yaTerminada ? "FINALIZADA" : "EN_CURSO");
            sessionRepository.save(session);
            cambiadas++;
        }
        for (Session session : sessionRepository.findByStatusAndEndTimeLessThanEqual("EN_CURSO", ahora)) {
            session.setStatus("FINALIZADA");
            sessionRepository.save(session);
            cambiadas++;
        }
        return cambiadas;
    }

    private Optional<Session> findRoomOverlap(String roomId, LocalDateTime start, LocalDateTime end, String excludeSessionId) {
        List<Session> roomSessions = sessionRepository.findByRoomIdAndStatusNot(roomId, "CANCELADA");

        return roomSessions.stream()
                .filter(s -> excludeSessionId == null || !excludeSessionId.equals(s.getId()))
                .filter(s -> s.getStartTime() != null && s.getEndTime() != null
                        && s.getStartTime().isBefore(end)
                        && s.getEndTime().isAfter(start))
                .findFirst();
    }
}

