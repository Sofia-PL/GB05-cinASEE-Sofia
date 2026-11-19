package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.dto.CheckInResponse;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del check-in de entradas en la puerta de sala.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String CODE = "QR-8901-5-12-ABCDEFGH";
    private static final Instant SHOW_START = Instant.parse("2026-10-16T16:30:00Z");

    @Mock
    private ReservedSeatRepository reservedSeatRepository;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(reservedSeatRepository, TestData.FIXED_CLOCK);
    }

    private static ReservedSeat seat(ReservationStatus status, Instant checkedInAt) {
        Reservation reservation = Reservation.builder()
                .id(8901L).showId(TestData.SHOW_ID).roomId(TestData.ROOM_ID)
                .showStartTime(SHOW_START).status(status).build();
        return ReservedSeat.builder()
                .reservation(reservation).showId(TestData.SHOW_ID)
                .seatRow(5).seatColumn(12).status(SeatStatus.SOLD)
                .ticketCode(CODE).checkedInAt(checkedInAt).build();
    }

    @Test
    void validTicketIsCheckedIn() {
        ReservedSeat seat = seat(ReservationStatus.CONFIRMED, null);
        when(reservedSeatRepository.findByTicketCode(CODE)).thenReturn(Optional.of(seat));

        CheckInResponse response = service.checkIn(CODE);

        assertThat(response.ticketCode()).isEqualTo(CODE);
        assertThat(response.reservationId()).isEqualTo(8901L);
        assertThat(response.showId()).isEqualTo(TestData.SHOW_ID);
        assertThat(response.roomId()).isEqualTo(TestData.ROOM_ID);
        assertThat(response.row()).isEqualTo(5);
        assertThat(response.column()).isEqualTo(12);
        assertThat(response.showStartTime()).isEqualTo(SHOW_START);
        assertThat(response.checkedInAt()).isEqualTo(TestData.NOW);
        assertThat(seat.getCheckedInAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void unknownTicketIsNotFound() {
        when(reservedSeatRepository.findByTicketCode("QR-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn("QR-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ticketAlreadyUsedIsRejected() {
        Instant earlier = TestData.NOW.minusSeconds(60);
        ReservedSeat seat = seat(ReservationStatus.CONFIRMED, earlier);
        when(reservedSeatRepository.findByTicketCode(CODE)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service.checkIn(CODE))
                .isInstanceOf(InvalidStateException.class)
                .extracting("errorCode").isEqualTo("TICKET_ALREADY_USED");
        assertThat(seat.getCheckedInAt()).isEqualTo(earlier);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "CONFIRMED", mode = EnumSource.Mode.EXCLUDE)
    void ticketOfNonConfirmedReservationIsNotValid(ReservationStatus status) {
        ReservedSeat seat = seat(status, null);
        when(reservedSeatRepository.findByTicketCode(CODE)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service.checkIn(CODE))
                .isInstanceOf(InvalidStateException.class)
                .extracting("errorCode").isEqualTo("TICKET_NOT_VALID");
        assertThat(seat.getCheckedInAt()).isNull();
    }
}
