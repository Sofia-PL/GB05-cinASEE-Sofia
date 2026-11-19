package es.cineasee.analitica.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del reenvío del JWT entrante a las llamadas Feign (token relay).
 */
class ForwardJwtFeignConfigTest {

    private final RequestInterceptor interceptor = new ForwardJwtFeignConfig().forwardJwtInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtOfIncomingRequestIsForwarded() {
        Jwt jwt = Jwt.withTokenValue("abc.def.ghi").header("alg", "RS256").subject("1")
                .issuedAt(Instant.parse("2026-10-08T12:00:00Z"))
                .expiresAt(Instant.parse("2026-10-08T13:00:00Z")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc.def.ghi");
    }

    @Test
    void withoutAuthenticationNoHeaderIsAdded() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void nonJwtAuthenticationIsNotForwarded() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass", List.of()));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}
