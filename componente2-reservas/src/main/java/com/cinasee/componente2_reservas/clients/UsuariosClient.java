package com.cinasee.componente2_reservas.clients;

import com.cinasee.componente2_reservas.clients.dto.UserProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente HTTP declarativo (OpenFeign) hacia el Componente 3 - Usuarios/IAM.
 *
 * COMUNICACIÓN SÍNCRONA de tipo CONSULTA: Reservas -> Usuarios -> GET /users/{userId}.
 * Reservas nunca accede a la base de datos MySQL de Usuarios.
 *
 * Se reenvía el JWT del usuario. El Componente 3 solo deja consultar un perfil
 * a su propietario o a STAFF_TAQUILLA/ADMIN, así que la propia llamada impide
 * que un cliente reserve haciéndose pasar por otro usuario.
 */
@FeignClient(
        name = "usuarios",
        url = "${app.clients.usuarios.url}",
        configuration = ForwardJwtFeignConfig.class
)
public interface UsuariosClient {

    @GetMapping("/users/{userId}")
    UserProfileDto getUserProfile(@PathVariable("userId") Long userId);
}
