package com.cinasee.componente2_reservas.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Butaca ocupada (bloqueada o vendida) en una sesión concreta.
 *
 * <p><b>Clave del control de concurrencia:</b> la restricción UNIQUE
 * (show_id, seat_row, seat_column) garantiza a nivel de base de datos que una
 * misma butaca de una misma sesión solo puede aparecer en UNA fila de esta tabla.
 * Si dos usuarios intentan bloquear la misma butaca a la vez, PostgreSQL acepta
 * la primera inserción y rechaza la segunda, aunque ambas peticiones hayan
 * superado las comprobaciones previas en memoria. Así se evita la doble venta.</p>
 *
 * <p>Cuando una reserva caduca, se cancela o se devuelve, sus filas se eliminan
 * y la butaca vuelve a quedar libre.</p>
 *
 * <p>Una vez pagada, cada butaca actúa también como <b>entrada</b>: guarda el
 * código único que se codifica en el QR/PKPass y la hora de acceso a sala.</p>
 */
@Entity
@Table(
        name = "reserved_seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reserved_seat_show_position",
                        columnNames = {"show_id", "seat_row", "seat_column"}),
                @UniqueConstraint(name = "uk_reserved_seat_ticket_code",
                        columnNames = "ticket_code")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    /** Se duplica el showId de la reserva para poder declarar el UNIQUE en esta tabla. */
    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "seat_column", nullable = false)
    private Integer seatColumn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatStatus status;

    /** Precio cobrado por esta butaca (con descuento). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Código único de la entrada (contenido del QR/PKPass). Nulo hasta que se paga. */
    @Column(name = "ticket_code", length = 60)
    private String ticketCode;

    /** Momento en que el personal de puerta validó la entrada. Nulo si no se ha usado. */
    @Column(name = "checked_in_at")
    private Instant checkedInAt;
}
