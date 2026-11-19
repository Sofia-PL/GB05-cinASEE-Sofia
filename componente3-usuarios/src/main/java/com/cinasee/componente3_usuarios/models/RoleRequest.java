package com.cinasee.componente3_usuarios.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Solicitud de cambio de rol presentada por un usuario.
 *
 * <p>El solicitante puede pedir STAFF_TAQUILLA o ADMIN. La solicitud queda en
 * PENDING hasta que un administrador global o el taquillero responsable del
 * cine la apruebe o rechace.</p>
 */
@Entity
@Table(name = "role_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role requestedRole;

    /**
     * Cine asociado a la solicitud. Obligatorio para STAFF_TAQUILLA y
     * opcional para ADMIN, que es global.
     */
    @Column
    private String cinemaId;

    @Column(length = 500)
    private String motivation;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleRequestStatus status = RoleRequestStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime reviewedAt;

    @Column
    private Long reviewedBy;

    @Column(length = 500)
    private String reviewComment;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = RoleRequestStatus.PENDING;
        }
    }
}
