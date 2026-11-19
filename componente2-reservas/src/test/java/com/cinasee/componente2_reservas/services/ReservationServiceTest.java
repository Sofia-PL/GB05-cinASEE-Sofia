package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.InvalidReservationException;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.SeatsUnavailableException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.DiscountType;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.repositories.ReservedSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de las reglas de negocio de POST /reservations.
 * Cartelera, Usuarios y la base de datos se sustituyen por mocks.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private RemoteDataService remoteDataService;
    @Mock
    private SeatLockService seatLockService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservedSeatRepository reservedSeatRepository;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(remoteDataService, new PricingService(TestData.pricingProperties()),
                seatLockService, reservationRepository, reservedSeatRepository,
                TestData.reservationProperties(), TestData.FIXED_CLOCK);
    }

    /** El mock de SeatLockService devuelve una reserva construida a partir del comando. */
    private void holdSucceeds() {
        when(seatLockService.hold(any())).thenAnswer(invocation -> {
            HoldCommand c = invocation.getArgument(0);
            return Reservation.builder().id(8901L).showId(c.showId()).movieId(c.movieId()).roomId(c.roomId())
                    .showStartTime(c.showStartTime()).userId(c.userId()).guestEmail(c.guestEmail())
                    .ownerSubject(c.ownerSubject()).status(ReservationStatus.PENDING_PAYMENT)
                    .unitPrice(c.quote().unitPrice()).totalAmount(c.quote().totalAmount()).currency(c.currency())
                    .discountType(c.quote().discountType()).seatCount(c.seats().size())
                    .createdAt(TestData.NOW).expiresAt(TestData.NOW.plusSeconds(600)).build();
        });
    }

    @Test
    void clienteRegistrado_reservaConDescuentoDeFidelizacion() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        when(remoteDataService.getUser(TestData.USER_ID)).thenReturn(TestData.user(1250));
        holdSucceeds();

        var response = service.createReservation(
                TestData.request(TestData.USER_ID, null, TestData.seats(8, 12, 8, 13)), TestData.CLIENT);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.totalAmount()).isEqualByComparingTo("15.30");
        assertThat(response.discountApplied()).isEqualTo(DiscountType.LOYALTY);
        assertThat(response.expiresAt()).isEqualTo(TestData.NOW.plusSeconds(600));
    }

    @Test
    void clienteRegistradoSinUserId_seUsaElDeSuToken() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        when(remoteDataService.getUser(TestData.USER_ID)).thenReturn(TestData.user(0));
        holdSucceeds();

        service.createReservation(TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT);

        ArgumentCaptor<HoldCommand> captor = ArgumentCaptor.forClass(HoldCommand.class);
        verify(seatLockService).hold(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(TestData.USER_ID);
        assertThat(captor.getValue().ownerSubject()).isEqualTo("5590");
    }

    @Test
    void clienteNoPuedeReservarANombreDeOtroUsuario() {
        assertThatThrownBy(() -> service.createReservation(
                TestData.request(TestData.USER_ID, null, TestData.seats(1, 1)), TestData.OTHER_CLIENT))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(remoteDataService, never()).getSession(anyString());
    }

    @Test
    void invitado_necesitaEmail() {
        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.GUEST))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("guestEmail");
    }

    @Test
    void invitado_conEmail_noConsultaUsuarios() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        holdSucceeds();

        var response = service.createReservation(
                TestData.request(null, "invitado@mail.com", TestData.seats(3, 4)), TestData.GUEST);

        assertThat(response.userId()).isNull();
        assertThat(response.guestEmail()).isEqualTo("invitado@mail.com");
        verify(remoteDataService, never()).getUser(anyLong());
    }

    @Test
    void invitado_noPuedeIndicarUserId() {
        assertThatThrownBy(() -> service.createReservation(
                TestData.request(TestData.USER_ID, "a@b.com", TestData.seats(1, 1)), TestData.GUEST))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void butacasRepetidas_400() {
        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(8, 12, 8, 12)), TestData.CLIENT))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("8-12");
    }

    @Test
    void butacaFueraDelMapaDeLaSala_400() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room()); // 10 x 15

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(11, 1)), TestData.CLIENT))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("11-1");
        verify(seatLockService, never()).hold(any());
    }

    @Test
    void sesionQueNoEstaALaVenta_409() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(
                TestData.session(TestData.SHOW_ID, "PROGRAMADA", TestData.FRIDAY_SHOW, BigDecimal.TEN));

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("no está a la venta");
    }

    @Test
    void sesionYaComenzada_409() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(
                TestData.session(TestData.SHOW_ID, "A_LA_VENTA", "2026-10-08T11:00:00", BigDecimal.TEN));

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("ya ha comenzado");
    }

    @Test
    void sesionInexistente_propagaEl404DeCartelera() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenThrow(new ResourceNotFoundException("no existe"));

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cartelerCaida_propagaEl503() {
        when(remoteDataService.getSession(TestData.SHOW_ID))
                .thenThrow(new ServiceUnavailableException("caído", null));

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void carreraConcurrente_violacionUnique_seConvierteEn409() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        when(remoteDataService.getUser(TestData.USER_ID)).thenReturn(TestData.user(0));
        when(seatLockService.hold(any())).thenThrow(new DataIntegrityViolationException("uk_reserved_seat_show_position"));

        assertThatThrownBy(() -> service.createReservation(
                TestData.request(null, null, TestData.seats(1, 1)), TestData.CLIENT))
                .isInstanceOf(SeatsUnavailableException.class);
    }

    @Test
    void taquilla_puedeVenderAUnClienteOAnonimo() {
        when(remoteDataService.getSession(TestData.SHOW_ID)).thenReturn(TestData.sessionOnSale());
        when(remoteDataService.getRoom(TestData.ROOM_ID)).thenReturn(TestData.room());
        holdSucceeds();

        // Venta anónima en taquilla: sin usuario y sin email
        var response = service.createReservation(TestData.request(null, null, TestData.seats(1, 1)), TestData.STAFF);

        assertThat(response.userId()).isNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void mapaDeButacas_ignoraBloqueosCaducados() {
        Reservation active = Reservation.builder().id(1L).status(ReservationStatus.PENDING_PAYMENT)
                .expiresAt(TestData.NOW.plusSeconds(60)).build();
        Reservation stale = Reservation.builder().id(2L).status(ReservationStatus.PENDING_PAYMENT)
                .expiresAt(Instant.parse("2026-10-08T11:59:00Z")).build();
        var seatActive = com.cinasee.componente2_reservas.models.ReservedSeat.builder().reservation(active)
                .seatRow(1).seatColumn(1).status(com.cinasee.componente2_reservas.models.SeatStatus.HELD).build();
        var seatStale = com.cinasee.componente2_reservas.models.ReservedSeat.builder().reservation(stale)
                .seatRow(1).seatColumn(2).status(com.cinasee.componente2_reservas.models.SeatStatus.HELD).build();
        when(reservedSeatRepository.findByShowIdWithReservation(TestData.SHOW_ID))
                .thenReturn(java.util.List.of(seatActive, seatStale));

        var map = service.getSeatMap(TestData.SHOW_ID);

        assertThat(map.occupiedSeats()).hasSize(1);
        assertThat(map.occupiedSeats().getFirst().column()).isEqualTo(1);
    }
}
