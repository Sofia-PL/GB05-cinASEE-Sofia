package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.dto.RoleRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestCreateRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestReviewRequest;
import com.cinasee.componente3_usuarios.services.RoleRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RoleRequestController implements RoleRequestsApi {

    private final RoleRequestService roleRequestService;

    /**
     * Un ADMIN no pasa el {@code @PreAuthorize} y recibe 403. El servicio
     * mantiene además un rechazo 409 por si se invocara directamente.
     */
    @Override
    @PreAuthorize("hasAnyRole('CLIENTE_REGISTRADO', 'STAFF_TAQUILLA')")
    public ResponseEntity<RoleRequest> createRoleRequest(RoleRequestCreateRequest roleRequestCreateRequest) {
        RoleRequest created = roleRequestService.createRoleRequest(
                currentUserId(),
                roleRequestCreateRequest
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    @PreAuthorize("hasAnyRole('CLIENTE_REGISTRADO', 'STAFF_TAQUILLA', 'ADMIN')")
    public ResponseEntity<List<RoleRequest>> getMyRoleRequests() {
        return ResponseEntity.ok(roleRequestService.getMyRoleRequests(currentUserId()));
    }

    /**
     * Solo entra un ADMIN o un STAFF. El servicio comprueba además que el
     * STAFF sea el taquillero responsable (cinemaManager) de su cine.
     */
    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_TAQUILLA')")
    public ResponseEntity<List<RoleRequest>> listRoleRequests(String status, String cinemaId) {
        return ResponseEntity.ok(roleRequestService.listRoleRequests(currentUserId(), status, cinemaId));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_TAQUILLA')")
    public ResponseEntity<RoleRequest> reviewRoleRequest(
            Long requestId,
            RoleRequestReviewRequest roleRequestReviewRequest
    ) {
        return ResponseEntity.ok(roleRequestService.reviewRoleRequest(
                currentUserId(),
                requestId,
                roleRequestReviewRequest
        ));
    }

    /**
     * El claim "sub" del JWT contiene el id del usuario, igual que en
     * UserController.
     */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto");
        }
        return Long.valueOf(authentication.getName());
    }
}
