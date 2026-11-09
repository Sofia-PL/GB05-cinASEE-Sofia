package es.cineasee.analitica.clients;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Configuración Feign que propaga el token JWT de la petición entrante a las
 * llamadas salientes hacia Cartelera y Usuarios ("token relay").
 *
 * <p>IMPORTANTE: esta clase NO lleva {@code @Configuration} a propósito. Si la
 * detectase el escaneo de componentes, el interceptor se aplicaría a TODOS los
 * clientes Feign. Solo se activa en los clientes que la referencian en
 * {@code @FeignClient(configuration = ...)}.</p>
 *
 * <p>Cuando la llamada se origina en un hilo sin petición HTTP (por ejemplo, el
 * listener de RabbitMQ), no hay JWT en el SecurityContext y el interceptor no
 * añade la cabecera. En ese caso la llamada solo funciona si el endpoint de
 * destino es público (como {@code GET /movies/{id}} de Cartelera).</p>
 */
public class ForwardJwtFeignConfig {

    @Bean
    public RequestInterceptor forwardJwtInterceptor() {
        return template -> {
            // En peticiones HTTP la llamada Feign se ejecuta en el mismo hilo,
            // así que el SecurityContext contiene el JWT ya validado del usuario.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                template.header("Authorization", "Bearer " + jwtAuth.getToken().getTokenValue());
            }
        };
    }
}
