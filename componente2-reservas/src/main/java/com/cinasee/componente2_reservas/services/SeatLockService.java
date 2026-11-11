package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.config.ReservationProperties;
import com.cinasee.componente2_reservas.dto.SeatSelection;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloqueo concurrente de butacas con TTL (Time To Live) de 10 minutos.
 *
 * <h2>Cómo se evita la doble venta</h2>
 * <ol>
 *   <li><b>Liberación de bloqueos caducados</b> de la sesión (con SELECT ... FOR UPDATE),
 *       para que una butaca abandonada vuelva a estar libre al instante.</li>
 *   <li><b>Comprobación previa</b>: si alguna butaca pedida ya está ocupada se devuelve
 *       409 indicando cuáles, sin llegar a escribir nada.</li>
 *   <li><b>Inserción protegida por la restricción UNIQUE</b>
 *       (show_id, seat_row, seat_column). Si dos peticiones pasan a la vez el paso 2,
 *       la base de datos solo deja confirmar a una; la otra recibe un error de
 *       integridad que {@link ReservationService} convierte en 409.</li>
 * </ol>
 * El paso 3 es la garantía real: no depende de que el código "llegue a tiempo",
 * funciona aunque haya varias instancias del microservicio detrás del API Gateway.
 *
 * <h2>Cómo caduca el bloqueo</h2>
 * Cada reserva guarda {@code expiresAt = createdAt + 10 min}. Al vencer:
 * <ul>
 *   <li>el barrido periódico ({@link ReservationExpirationScheduler}) la marca EXPIRED y
 *       borra sus butacas;</li>
 *   <li>y, por si el barrido aún no ha pasado, cada nueva reserva de la sesión y cada
 *       intento de pago comprueban también la caducidad ("expiración perezosa").</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ReservationProperties properties;
    private final Clock clock;

    /**
     * Bloquea las butacas y crea la reserva en estado PENDING_PAYMENT, todo en una
     * única transacción ACID (o se crea todo o no se crea nada).
     *
     * @throws SeatsUnavailableException si alguna butaca ya está bloqueada o vendida
     */
    @Transactional
    public Reservation hold(HoldCommand command) {
        Instant now = clock.instant();

        // 1) Liberar las reservas caducadas de ESTA sesión antes de comprobar huecos
        List<Reservation> stale = reservationRepository.findExpiredByShowForUpdate(
                command.showId(), ReservationStatus.PENDING_PAYMENT, now, now.minus(properties.paymentGrace()));
        release(stale, ReservationStatus.EXPIRED, now);

        // 2) Comprobación previa de disponibilidad (da un mensaje claro al usuario)
        Set<String> requested = command.seats().stream()
                .map(SeatSelection::label)
                .collect(Collectors.toSet());
        List<String> conflicts = reservedSeatRepository.findByShowIdWithReservation(command.showId()).stream()
                .map(seat -> seat.getSeatRow() + "-" + seat.getSeatColumn())
                .filter(requested::contains)
                .sorted()
                .toList();
        if (!conflicts.isEmpty()) {
            throw new SeatsUnavailableException(conflicts);
        }

        // 3) Crear la reserva y sus butacas. saveAndFlush fuerza los INSERT ahora mismo,
        //    de forma que una violación del UNIQUE se detecte dentro de este método.
        Reservation reservation = Reservation.builder()
                .showId(command.showId())
                .movieId(command.movieId())
                .roomId(command.roomId())
                .showStartTime(command.showStartTime())
                .userId(command.userId())
                .guestEmail(command.guestEmail())
                .ownerSubject(command.ownerSubject())
                .status(ReservationStatus.PENDING_PAYMENT)
                .unitPrice(command.quote().unitPrice())
                .totalAmount(command.quote().totalAmount())
                .currency(command.currency())
                .discountType(command.quote().discountType())
                .seatCount(command.seats().size())
                .createdAt(now)
                .expiresAt(now.plus(properties.holdTtl()))
                .build();

        for (SeatSelection selection : command.seats()) {
            reservation.addSeat(ReservedSeat.builder()
                    .showId(command.showId())
                    .seatRow(selection.row())
                    .seatColumn(selection.column())
                    .status(SeatStatus.HELD)
                    .price(command.quote().unitPrice())
                    .build());
        }

        Reservation saved = reservationRepository.saveAndFlush(reservation);
        log.info("Reserva {} creada: sesión {}, butacas {}, total {} {}, caduca {}",
                saved.getId(), saved.getShowId(), requested, saved.getTotalAmount(),
                saved.getCurrency(), saved.getExpiresAt());
        return saved;
    }

    /**
     * Barrido periódico: marca como EXPIRED un lote de reservas pendientes cuyo TTL
     * ha vencido y libera sus butacas.
     *
     * @return número de reservas caducadas en esta pasada
     */
    @Transactional
    public int expireOverdueReservations() {
        Instant now = clock.instant();
        List<Reservation> overdue = reservationRepository.findExpiredForUpdate(
                ReservationStatus.PENDING_PAYMENT, now, now.minus(properties.paymentGrace()),
                PageRequest.of(0, properties.expirationBatch()));
        release(overdue, ReservationStatus.EXPIRED, now);
        return overdue.size();
    }

    /**
     * Cierra las reservas indicadas con el estado final dado y borra sus butacas
     * para que puedan volver a venderse.
     *
     * Debe llamarse dentro de una transacción existente y con las reservas ya
     * bloqueadas (FOR UPDATE); por eso exige Propagation.MANDATORY.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(List<Reservation> reservations, ReservationStatus finalStatus, Instant now) {
        if (reservations.isEmpty()) {
            return;
        }
        List<Long> ids = reservations.stream().map(Reservation::getId).toList();
        int freedSeats = reservedSeatRepository.deleteByReservationIds(ids);
        for (Reservation reservation : reservations) {
            reservation.setStatus(finalStatus);
            reservation.setClosedAt(now);
        }
        log.info("Reservas {} -> {} ({} butacas liberadas)", ids, finalStatus, freedSeats);
    }
}
