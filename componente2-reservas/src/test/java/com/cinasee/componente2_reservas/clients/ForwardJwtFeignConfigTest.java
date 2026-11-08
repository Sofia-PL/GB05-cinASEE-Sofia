package com.cinasee.componente2_reservas.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el "token relay": el JWT de la petición entrante se reenvía en las
 * llamadas Feign a Cartelera y Usuarios.
 */
class ForwardJwtFeignConfigTest {

    private final RequestInterceptor interceptor = new ForwardJwtFeignConfig().forwardJwtInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reenviaElTokenDelUsuarioAutenticado() {
        Jwt jwt = Jwt.withTokenValue("eyJ.token.firmado")
                .header("alg", "RS256").subject("5590").claim("role", "CLIENTE_REGISTRADO")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer eyJ.token.firmado");
    }

    @Test
    void sinAutenticacion_noAnadeCabecera() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}
