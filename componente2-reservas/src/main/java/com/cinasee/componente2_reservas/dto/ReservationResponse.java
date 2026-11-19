package com.cinasee.componente2_reservas.dto;

import com.cinasee.componente2_reservas.models.DiscountType;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.models.ReservedSeat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Representación pública de una reserva (schema Reservation del OpenAPI).
 * Incluye los campos de la especificación original (id, showId, userId,
 * totalAmount, status, expiresAt) más información útil para el frontend.
 */
public record ReservationResponse(
        Long id,
        String showId,
        String movieId,
        String roomId,
        Instant showStartTime,
        Long userId,
        String guestEmail,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        DiscountType discountApplied,
        ReservationStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Integer seatCount,
        List<SeatResponse> seats
) {

    /** Butaca dentro de la reserva; ticketCode solo aparece cuando está pagada. */
    public record SeatResponse(Integer row, Integer column, BigDecimal price, String ticketCode) {
        static SeatResponse from(ReservedSeat seat) {
            return new SeatResponse(seat.getSeatRow(), seat.getSeatColumn(), seat.getPrice(), seat.getTicketCode());
        }
    }

    /**
     * Convierte la entidad JPA en DTO (las butacas se ordenan por fila y columna).
     * Las reservas cerradas (EXPIRED, CANCELLED, REFUNDED) ya no ocupan butacas:
     * se devuelve la lista vacía y {@code seatCount} conserva el dato histórico.
     */
    public static ReservationResponse from(Reservation r) {
        boolean holdsSeats = r.getStatus() == ReservationStatus.PENDING_PAYMENT
                || r.getStatus() == ReservationStatus.CONFIRMED;
        List<SeatResponse> seats = !holdsSeats ? List.of() : r.getSeats().stream()
                .sorted(Comparator.comparing(ReservedSeat::getSeatRow).thenComparing(ReservedSeat::getSeatColumn))
                .map(SeatResponse::from)
                .toList();
        return new ReservationResponse(
                r.getId(), r.getShowId(), r.getMovieId(), r.getRoomId(), r.getShowStartTime(),
                r.getUserId(), r.getGuestEmail(), r.getUnitPrice(), r.getTotalAmount(), r.getCurrency(),
                r.getDiscountType(), r.getStatus(), r.getCreatedAt(), r.getExpiresAt(), r.getPaidAt(),
                r.getSeatCount(), seats);
    }
}
