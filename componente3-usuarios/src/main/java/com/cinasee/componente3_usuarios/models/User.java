package com.cinasee.componente3_usuarios.models;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.CLIENTE_REGISTRADO;

    @Builder.Default
    @Min(value = 0, message = "El saldo de puntos no puede ser negativo")
    @Column(nullable = false)
    private Integer loyaltyPoints = 0;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String homeCinema;

    /**
     * Cine al que está asignado el usuario cuando tiene un rol interno.
     * Es nulo para los clientes registrados.
     */
    @Column
    private String cinemaId;

    /**
     * Marca al taquillero "original" de un cine: además de vender entradas,
     * puede revisar las solicitudes STAFF_TAQUILLA de su propio cine.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean cinemaManager = false;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_preferred_genres",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "genre", nullable = false, length = 60)
    private List<String> preferredGenres = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (role == null) {
            role = Role.CLIENTE_REGISTRADO;
        }
        if (loyaltyPoints == null) {
            loyaltyPoints = 0;
        }
        if (preferredGenres == null) {
            preferredGenres = new ArrayList<>();
        }
    }
}
