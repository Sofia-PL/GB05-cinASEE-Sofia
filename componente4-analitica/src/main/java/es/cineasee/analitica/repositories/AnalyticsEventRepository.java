package es.cineasee.analitica.repositories;

import es.cineasee.analitica.models.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("SELECT COALESCE(SUM(e.ticketsSold), 0) FROM AnalyticsEvent e " +
           "WHERE e.cinemaId = :cinemaId " +
           "AND (:roomId IS NULL OR e.roomId = :roomId) " +
           "AND (:dayOfWeek IS NULL OR e.dayOfWeek = :dayOfWeek)")
    Long sumTicketsSold(
            @Param("cinemaId") String cinemaId,
            @Param("roomId") String roomId,
            @Param("dayOfWeek") Integer dayOfWeek
    );

    @Query("SELECT COALESCE(AVG(e.ticketsSold), 0.0) FROM AnalyticsEvent e " +
           "WHERE e.cinemaId = :cinemaId " +
           "AND (:roomId IS NULL OR e.roomId = :roomId)")
    Double avgTicketsSoldPerEvent(
            @Param("cinemaId") String cinemaId,
            @Param("roomId") String roomId
    );

    @Query("SELECT e.movieId, e.movieTitle, e.distributor, " +
           "SUM(e.ticketsSold), SUM(e.totalAmount) " +
           "FROM AnalyticsEvent e " +
           "WHERE e.eventTimestamp >= :start AND e.eventTimestamp <= :end " +
           "AND (:movieId IS NULL OR e.movieId = :movieId) " +
           "AND (:distributorId IS NULL OR e.distributorId = :distributorId) " +
           "GROUP BY e.movieId, e.movieTitle, e.distributor")
    List<Object[]> findBoxOfficeAggregate(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("movieId") String movieId,
            @Param("distributorId") Long distributorId
    );

    @Query("SELECT e.roomId, SUM(e.ticketsSold), AVG(e.ticketsSold) " +
           "FROM AnalyticsEvent e " +
           "WHERE e.cinemaId = :cinemaId " +
           "GROUP BY e.roomId")
    List<Object[]> findRoomAggregationsByCinema(@Param("cinemaId") String cinemaId);

    @Query("SELECT e.genre, COUNT(e), SUM(e.ticketsSold) " +
           "FROM AnalyticsEvent e " +
           "WHERE e.userId = :userId " +
           "GROUP BY e.genre " +
           "ORDER BY SUM(e.ticketsSold) DESC")
    List<Object[]> findTopGenresByUserId(@Param("userId") Long userId);

    List<AnalyticsEvent> findByUserId(Long userId);
}

