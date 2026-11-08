package com.cinasee.componente2_reservas.clients;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Configuración Feign que propaga el token JWT de la petición entrante a las
 * llamadas salientes hacia Cartelera y Usuarios ("token relay").
 *
 * IMPORTANTE: esta clase NO lleva @Configuration a propósito. Si la detectase el
 * escaneo de componentes, el interceptor se aplicaría a TODOS los clientes Feign,
 * incluida la pasarela de pago externa, y le enviaríamos el JWT de nuestros
 * usuarios a un tercero. Solo se activa en los clientes que la referencian en
 * {@code @FeignClient(configuration = ...)}.
 */
public class ForwardJwtFeignConfig {

    @Bean
    public RequestInterceptor forwardJwtInterceptor() {
        return template -> {
            // La llamada Feign se ejecuta en el mismo hilo que la petición HTTP,
            // así que el SecurityContext contiene el JWT ya validado del usuario.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                template.header("Authorization", "Bearer " + jwtAuth.getToken().getTokenValue());
            }
        };
    }
}
