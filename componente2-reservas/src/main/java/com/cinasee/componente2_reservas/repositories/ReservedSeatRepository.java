package com.cinasee.componente2_reservas.repositories;

import com.cinasee.componente2_reservas.models.ReservedSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de las butacas ocupadas / entradas.
 */
public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {

    /**
     * Todas las butacas ocupadas de una sesión, junto con su reserva
     * (join fetch para evitar el problema N+1 al leer el estado de la reserva).
     */
    @Query("select s from ReservedSeat s join fetch s.reservation where s.showId = :showId")
    List<ReservedSeat> findByShowIdWithReservation(@Param("showId") String showId);

    /**
     * Busca una entrada por su código QR (validación en puerta) bloqueándola para
     * escritura, de modo que dos lectores no puedan validar a la vez la misma entrada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReservedSeat s join fetch s.reservation where s.ticketCode = :ticketCode")
    Optional<ReservedSeat> findByTicketCode(@Param("ticketCode") String ticketCode);

    /**
     * Libera de golpe las butacas de varias reservas (DELETE masivo en JPQL).
     *
     * Se usa una sentencia masiva y no {@code reservation.getSeats().clear()}
     * porque Hibernate ejecuta los INSERT antes que los DELETE al hacer flush:
     * si en la misma transacción se liberan butacas caducadas y se vuelven a
     * bloquear para otra reserva, el INSERT chocaría con la restricción UNIQUE.
     * La sentencia masiva se ejecuta en el acto, antes de los nuevos INSERT.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReservedSeat s where s.reservation.id in :reservationIds")
    int deleteByReservationIds(@Param("reservationIds") Collection<Long> reservationIds);
}
