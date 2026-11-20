package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.dto.AssignRoleRequest;
import com.cinasee.componente3_usuarios.dto.PurchaseHistoryItem;
import com.cinasee.componente3_usuarios.dto.UpdateUserProfileRequest;
import com.cinasee.componente3_usuarios.dto.UserProfile;
import com.cinasee.componente3_usuarios.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {

    private final UserService userService;

    /**
     * Un cliente solo puede consultar su propio perfil; STAFF y ADMIN pueden
     * consultar el de cualquier usuario (por ejemplo, desde taquilla).
     *
     * authentication.name contiene el claim "sub" del JWT, que JwtProvider
     * rellena con el id del usuario.
     */
    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_TAQUILLA') or #userId.toString() == authentication.name")
    public ResponseEntity<UserProfile> getUserProfile(@P("userId") Long userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    /**
     * Solo el propio usuario o un ADMIN pueden modificar un perfil.
     * STAFF_TAQUILLA puede consultar, pero no editar datos personales.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
    public ResponseEntity<Void> updateUserProfile(
            @P("userId") Long userId,
            UpdateUserProfileRequest updateUserProfileRequest
    ) {
        userService.updateUserProfile(userId, updateUserProfileRequest);
        return ResponseEntity.ok().build();
    }

    /**
     * Mismo criterio que el perfil: el historial es dato personal, así que solo
     * lo ve el propio cliente o los roles internos autorizados.
     */
    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_TAQUILLA') or #userId.toString() == authentication.name")
    public ResponseEntity<List<PurchaseHistoryItem>> getUserHistory(@P("userId") Long userId) {
        return ResponseEntity.ok(userService.getUserHistory(userId));
    }

    /**
     * Asignación directa de rol a un usuario por parte de un Administrador.
     */
    @PutMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignUserRole(
            @PathVariable Long userId,
            @RequestBody @Valid AssignRoleRequest request
    ) {
        userService.assignRole(userId, request);
        return ResponseEntity.ok().build();
    }
}
