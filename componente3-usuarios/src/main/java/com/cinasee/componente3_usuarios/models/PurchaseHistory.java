package com.cinasee.componente3_usuarios.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "purchase_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_history_reservation",
                columnNames = "reservation_id"
        ),
        indexes = @Index(
                name = "idx_purchase_history_user",
                columnList = "user_id"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "movie_id", nullable = false)
    private String movieId;

    @Column(name = "purchase_date", nullable = false)
    private OffsetDateTime purchaseDate;

    @DecimalMin(value = "0.0", message = "El importe de la compra no puede ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
