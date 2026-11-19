package com.cinasee.componente2_reservas.repositories;

import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de las reservas (Spring Data JPA sobre PostgreSQL).
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Carga una reserva aplicando un bloqueo pesimista de escritura
     * (SELECT ... FOR UPDATE en PostgreSQL).
     *
     * Se usa en el pago, la cancelación y la devolución: mientras dure la
     * transacción ninguna otra (por ejemplo, el barrido de reservas caducadas)
     * puede modificar esa fila, de modo que no puede ocurrir que una reserva se
     * marque como EXPIRED mientras se está cobrando.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Reservas pendientes de una sesión cuyo TTL ya ha vencido, bloqueadas para
     * escritura. Se liberan justo antes de bloquear butacas nuevas en esa sesión,
     * así una butaca abandonada vuelve a estar disponible al instante sin esperar
     * al barrido periódico.
     *
     * Con FOR UPDATE, si una de estas reservas se está pagando en ese momento,
     * esta consulta espera a que termine el pago y PostgreSQL vuelve a evaluar el
     * WHERE: si ya está CONFIRMED, deja de aparecer en el resultado.
     *
     * No se incluyen las que tienen un pago en Redsys en curso (iniciado después de
     * {@code graceLimit} = ahora − margen de pago): el cliente está pagando.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.showId = :showId
              and r.status = :status
              and r.expiresAt <= :now
              and (r.paymentStartedAt is null or r.paymentStartedAt <= :graceLimit)
            """)
    List<Reservation> findExpiredByShowForUpdate(@Param("showId") String showId,
                                                 @Param("status") ReservationStatus status,
                                                 @Param("now") Instant now,
                                                 @Param("graceLimit") Instant graceLimit);

    /**
     * Lote de reservas pendientes caducadas en cualquier sesión, para el
     * barrido programado (ReservationExpirationScheduler). Igual que la anterior,
     * respeta el margen de las reservas con un pago en Redsys en curso.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.status = :status
              and r.expiresAt <= :now
              and (r.paymentStartedAt is null or r.paymentStartedAt <= :graceLimit)
            order by r.expiresAt
            """)
    List<Reservation> findExpiredForUpdate(@Param("status") ReservationStatus status,
                                           @Param("now") Instant now,
                                           @Param("graceLimit") Instant graceLimit,
                                           Pageable pageable);

    /**
     * Busca, bloqueándola, la reserva a la que pertenece un número de pedido de Redsys.
     * Se usa al volver de la web de Redsys: el pedido viene dentro de la respuesta firmada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.paymentOrder = :order")
    Optional<Reservation> findByPaymentOrderForUpdate(@Param("order") String order);

    /**
     * Reservas creadas por un sujeto JWT concreto (las "mis entradas" del invitado).
     * El EntityGraph carga las butacas en la misma consulta (evita el problema N+1).
     */
    @EntityGraph(attributePaths = "seats")
    List<Reservation> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

    /** Reservas asociadas a un usuario registrado (cliente o consulta de taquilla/admin). */
    @EntityGraph(attributePaths = "seats")
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Agregado de entradas vendidas y recaudación por sesión, para el cálculo de
     * ocupación del Componente 4 (GET /reservations/stats).
     * Solo cuentan las reservas confirmadas (las devueltas no ocupan butaca).
     */
    @Query("""
            select new com.cinasee.componente2_reservas.repositories.ShowSalesAggregate(
                r.showId, r.movieId, r.roomId, r.showStartTime,
                sum(r.seatCount), sum(r.totalAmount), count(r))
            from Reservation r
            where r.status = com.cinasee.componente2_reservas.models.ReservationStatus.CONFIRMED
              and r.showStartTime >= :from
              and r.showStartTime < :to
            group by r.showId, r.movieId, r.roomId, r.showStartTime
            order by r.showStartTime
            """)
    List<ShowSalesAggregate> aggregateSalesByShow(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Agregado de taquilla por película según la fecha de pago, para los informes
     * Box Office del Componente 4 (GET /reservations/box-office-data).
     */
    @Query("""
            select new com.cinasee.componente2_reservas.repositories.MovieSalesAggregate(
                r.movieId, sum(r.seatCount), sum(r.totalAmount), count(r))
            from Reservation r
            where r.status = com.cinasee.componente2_reservas.models.ReservationStatus.CONFIRMED
              and r.paidAt >= :from
              and r.paidAt < :to
            group by r.movieId
            order by r.movieId
            """)
    List<MovieSalesAggregate> aggregateSalesByMovie(@Param("from") Instant from, @Param("to") Instant to);
}
