package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<Session, Long> {
    List<Session> findByMovieId(Long movieId);
    List<Session> findByRoomId(Long roomId);
    List<Session> findByRoomIdAndStatusNot(Long roomId, String status);
    List<Session> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Session> findByMovieIdAndStartTimeBetween(Long movieId, LocalDateTime start, LocalDateTime end);
}

