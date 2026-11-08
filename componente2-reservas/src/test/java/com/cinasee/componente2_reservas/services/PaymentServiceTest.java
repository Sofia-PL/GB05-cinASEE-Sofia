package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.dto.PaymentRequest;
import com.cinasee.componente2_reservas.events.OutboxService;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.DiscountType;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;
import com.cinasee.componente2_reservas.models.SeatStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.services.payment.SimulatedPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del pago: cobro, emisión de entradas, TTL y evento PaymentCompleted.
 * Usa la pasarela simulada real (su comportamiento depende del token).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final PaymentRequest OK = new PaymentRequest(PaymentRequest.PaymentGatewayType.REDSYS, "tok_visa_ok");
    private static final PaymentRequest DECLINED = new PaymentRequest(PaymentRequest.PaymentGatewayType.REDSYS, "tok_declined_card");
    private static final PaymentRequest GATEWAY_DOWN = new PaymentRequest(PaymentRequest.PaymentGatewayType.REDSYS, "tok_unavailable");

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatLockService seatLockService;
    @Mock
    private OutboxService outboxService;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(reservationRepository, seatLockService, new SimulatedPaymentGateway(),
                new TicketCodeGenerator(), outboxService, TestData.FIXED_CLOCK);
    }

    private Reservation pendingReservation(Instant expiresAt) {
        Reservation reservation = Reservation.builder()
                .id(8901L).showId(TestData.SHOW_ID).movieId(TestData.MOVIE_ID).roomId(TestData.ROOM_ID)
                .showStartTime(Instant.parse("2026-10-16T18:30:00Z"))
                .userId(TestData.USER_ID).ownerSubject("5590")
                .status(ReservationStatus.PENDING_PAYMENT)
                .unitPrice(new BigDecimal("9.25")).totalAmount(new BigDecimal("18.50")).currency("EUR")
                .discountType(DiscountType.NONE).seatCount(2)
                .createdAt(TestData.NOW.minusSeconds(60)).expiresAt(expiresAt)
                .build();
        reservation.addSeat(ReservedSeat.builder().showId(TestData.SHOW_ID).seatRow(8).seatColumn(13)
                .status(SeatStatus.HELD).price(new BigDecimal("9.25")).build());
        reservation.addSeat(ReservedSeat.builder().showId(TestData.SHOW_ID).seatRow(8).seatColumn(12)
                .status(SeatStatus.HELD).price(new BigDecimal("9.25")).build());
        return reservation;
    }

    @Test
    void pagoAprobado_confirmaEmiteEntradasYEncolaPaymentCompleted() {
        Reservation reservation = pendingReservation(TestData.NOW.plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        var confirmation = service.payReservation(8901L, OK, TestData.CLIENT);

        assertThat(confirmation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmation.tickets()).hasSize(2);
        // Ordenadas por butaca y con el formato QR-{reserva}-{fila}-{columna}-{aleatorio}
        assertThat(confirmation.tickets().get(0)).startsWith("QR-8901-8-12-");
        assertThat(confirmation.tickets().get(1)).startsWith("QR-8901-8-13-");
        assertThat(confirmation.paymentReference()).startsWith("SIM-");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getPaidAt()).isEqualTo(TestData.NOW);
        assertThat(reservation.getSeats()).allMatch(seat -> seat.getStatus() == SeatStatus.SOLD);
        verify(outboxService).enqueuePaymentCompleted(reservation);
    }

    @Test
    void pagoRechazado_402_yLaReservaSiguePendiente() {
        Reservation reservation = pendingReservation(TestData.NOW.plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, DECLINED, TestData.CLIENT))
                .isInstanceOf(PaymentDeclinedException.class);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        verify(outboxService, never()).enqueuePaymentCompleted(any());
    }

    @Test
    void pasarelaCaida_503() {
        Reservation reservation = pendingReservation(TestData.NOW.plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, GATEWAY_DOWN, TestData.CLIENT))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(outboxService, never()).enqueuePaymentCompleted(any());
    }

    @Test
    void ttlVencido_400_yLiberaLasButacas() {
        // Expiró hace 1 segundo: los 10 minutos ya han pasado
        Reservation reservation = pendingReservation(TestData.NOW.minusSeconds(1));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, OK, TestData.CLIENT))
                .isInstanceOf(ReservationExpiredException.class);

        verify(seatLockService).release(List.of(reservation), ReservationStatus.EXPIRED, TestData.NOW);
        verify(outboxService, never()).enqueuePaymentCompleted(any());
    }

    @Test
    void ttlJustoEnElLimite_seConsideraCaducado() {
        Reservation reservation = pendingReservation(TestData.NOW);
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, OK, TestData.CLIENT))
                .isInstanceOf(ReservationExpiredException.class);
    }

    @Test
    void reservaYaPagada_409() {
        Reservation reservation = pendingReservation(TestData.NOW.plusSeconds(300));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, OK, TestData.CLIENT))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("ya está pagada");
    }

    @Test
    void reservaDeOtroUsuario_403() {
        Reservation reservation = pendingReservation(TestData.NOW.plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(8901L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.payReservation(8901L, OK, TestData.OTHER_CLIENT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void reservaInexistente_404() {
        when(reservationRepository.findByIdForUpdate(eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payReservation(1L, OK, TestData.CLIENT))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
