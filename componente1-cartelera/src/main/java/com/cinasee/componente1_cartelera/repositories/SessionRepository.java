package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
    List<Session> findByMovieId(String movieId);
    List<Session> findByRoomId(String roomId);
    List<Session> findByRoomIdAndStatusNot(String roomId, String status);
    List<Session> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Session> findByMovieIdAndStartTimeBetween(String movieId, LocalDateTime start, LocalDateTime end);

    /** Sesiones en alguno de estos estados cuya hora de inicio ya ha llegado */
    List<Session> findByStatusInAndStartTimeLessThanEqual(List<String> statuses, LocalDateTime time);

    /** Sesiones en este estado cuya hora de fin ya ha llegado */
    List<Session> findByStatusAndEndTimeLessThanEqual(String status, LocalDateTime time);
}

