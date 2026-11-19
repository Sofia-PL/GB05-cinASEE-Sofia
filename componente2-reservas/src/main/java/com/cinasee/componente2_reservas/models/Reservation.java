package com.cinasee.componente2_reservas.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserva de entradas (agregado raíz del dominio de Reservas).
 *
 * <p>Los campos {@code showId}, {@code movieId}, {@code roomId} y {@code showStartTime}
 * son una <b>instantánea</b> (snapshot) de los datos que devolvió el Componente 1
 * (Cartelera) en el momento de reservar. Guardarlos aquí NO rompe el patrón
 * "Database per Service": no hay ninguna clave foránea hacia otra base de datos,
 * solo referencias por identificador que permiten generar estadísticas
 * (Componente 4) sin tener que volver a consultar a Cartelera.</p>
 *
 * <p>Del mismo modo, {@code userId} es una referencia al Componente 3 (Usuarios)
 * y es nulo en las compras de invitado, que se vinculan solo a {@code guestEmail}.</p>
 */
@Entity
@Table(
        name = "reservations",
        indexes = {
                // Acelera el barrido de reservas caducadas (status + expires_at)
                @Index(name = "idx_reservations_status_expires", columnList = "status, expires_at"),
                @Index(name = "idx_reservations_show", columnList = "show_id"),
                @Index(name = "idx_reservations_owner", columnList = "owner_subject")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador de la sesión/pase (Componente 1). */
    @Column(name = "show_id", nullable = false)
    private String showId;

    /** Película proyectada en la sesión (snapshot del Componente 1). */
    @Column(name = "movie_id", nullable = false)
    private String movieId;

    /** Sala de la sesión (snapshot del Componente 1). */
    @Column(name = "room_id", nullable = false)
    private String roomId;

    /** Hora de inicio de la sesión (snapshot del Componente 1). */
    @Column(name = "show_start_time", nullable = false)
    private Instant showStartTime;

    /** Usuario registrado (Componente 3). Nulo en compras de invitado. */
    @Column(name = "user_id")
    private Long userId;

    /** Correo al que se vincula una compra de invitado. */
    @Column(name = "guest_email", length = 150)
    private String guestEmail;

    /**
     * Claim "sub" del JWT de quien creó la reserva. Sirve para comprobar que
     * solo el propietario (o el personal de taquilla/administración) puede
     * consultarla, pagarla o cancelarla. En invitados vale "guest:&lt;uuid&gt;".
     */
    @Column(name = "owner_subject", nullable = false, length = 100)
    private String ownerSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /** Precio unitario por butaca, ya con el descuento aplicado. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Importe total a cobrar (unitPrice x número de butacas). */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /**
     * Número de butacas de la reserva. Se guarda aparte porque, al caducar o
     * devolverse una reserva, sus filas de reserved_seats se eliminan para
     * liberar las butacas, pero queremos conservar el dato como histórico.
     */
    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Instante en que caduca el bloqueo temporal (createdAt + TTL de 10 min). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Fecha de cancelación, caducidad o devolución. */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** Pasarela usada en el cobro (REDSYS). */
    @Column(name = "payment_gateway", length = 20)
    private String paymentGateway;

    /** Identificador de la transacción devuelto por la pasarela. */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * Número de pedido enviado a Redsys en el último intento de pago (Ds_Merchant_Order,
     * 12 dígitos). Es único: Redsys rechaza pedidos repetidos y, además, sirve para
     * encontrar la reserva cuando el cliente vuelve de la web de Redsys.
     */
    @Column(name = "payment_order", length = 12, unique = true)
    private String paymentOrder;

    /** Momento en que se inició el último pago en Redsys. Nulo si no hay ninguno en curso. */
    @Column(name = "payment_started_at")
    private Instant paymentStartedAt;

    /**
     * Bloqueo optimista: si dos transacciones modifican la misma reserva a la vez,
     * la segunda falla en lugar de sobrescribir silenciosamente a la primera.
     * Es una segunda red de seguridad además del bloqueo pesimista (SELECT ... FOR UPDATE)
     * que usan el pago, la cancelación y el barrido de caducadas.
     */
    @Version
    private Long version;

    /** Butacas bloqueadas/vendidas. Se persisten en cascada al crear la reserva. */
    @Builder.Default
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservedSeat> seats = new ArrayList<>();

    /** Añade una butaca manteniendo sincronizados ambos lados de la relación. */
    public void addSeat(ReservedSeat seat) {
        seat.setReservation(this);
        seats.add(seat);
    }

    /** Indica si la reserva sigue pendiente pero su TTL ya ha vencido en el instante indicado. */
    public boolean isHoldExpiredAt(Instant now) {
        return status == ReservationStatus.PENDING_PAYMENT && !now.isBefore(expiresAt);
    }

    /**
     * Indica si hay un pago en Redsys en curso (el cliente está en la web de Redsys)
     * dentro del margen indicado. Mientras sea así, la reserva no se caduca aunque
     * hayan pasado sus 10 minutos, para no liberar butacas que se están pagando.
     */
    public boolean isPaymentInProgressAt(Instant now, Duration grace) {
        return status == ReservationStatus.PENDING_PAYMENT
                && paymentStartedAt != null
                && now.isBefore(paymentStartedAt.plus(grace));
    }
}
