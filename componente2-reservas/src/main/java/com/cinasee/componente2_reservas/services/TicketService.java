package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.dto.CheckInResponse;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Caso de uso: validación de entradas en la puerta de sala (Portal de validación).
 * POST /reservations/tickets/{ticketCode}/check-in, solo STAFF_TAQUILLA / ADMIN.
 *
 * Una entrada solo puede usarse una vez. Si dos lectores escanean el mismo QR a
 * la vez, el bloqueo pesimista de la fila de la entrada hace que solo uno la valide.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final ReservedSeatRepository reservedSeatRepository;
    private final Clock clock;

    @Transactional
    public CheckInResponse checkIn(String ticketCode) {
        ReservedSeat seat = reservedSeatRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("La entrada " + ticketCode + " no existe"));
        Reservation reservation = seat.getReservation();

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new InvalidStateException("TICKET_NOT_VALID",
                    "La entrada no es válida: la reserva está " + reservation.getStatus());
        }
        if (seat.getCheckedInAt() != null) {
            throw new InvalidStateException("TICKET_ALREADY_USED",
                    "La entrada ya se validó el " + seat.getCheckedInAt());
        }

        Instant now = clock.instant();
        // La fila está bloqueada (FOR UPDATE) hasta el commit: un segundo lector que
        // escanee el mismo QR esperará y verá checkedInAt ya relleno.
        seat.setCheckedInAt(now);

        return new CheckInResponse(ticketCode, reservation.getId(), reservation.getShowId(), reservation.getRoomId(),
                seat.getSeatRow(), seat.getSeatColumn(), reservation.getShowStartTime(), now);
    }
}
