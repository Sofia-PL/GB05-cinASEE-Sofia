package com.cinasee.componente2_reservas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * SOLO para el perfil "isolated" (probar el microservicio sin el Componente 3).
 *
 * En producción los tokens los firma el Componente 3 con RSA y aquí se validan con
 * su JWKS. Para probar Reservas de forma aislada no tenemos ese servicio, así que
 * este perfil sustituye el decodificador por uno HMAC-SHA256 con un secreto de
 * desarrollo. Los tokens de prueba se generan con scripts/generate-dev-token.ps1.
 *
 * Al declarar un bean JwtDecoder, Spring Boot desactiva su decodificador por
 * defecto (el del JWKS). Este perfil NUNCA debe activarse en producción.
 */
@Configuration
@Profile("isolated")
public class IsolatedJwtConfig {

    @Bean
    public JwtDecoder isolatedJwtDecoder(
            @Value("${app.security.dev-hmac-secret}") String secret,
            @Value("${app.security.dev-issuer}") String issuer) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.security.dev-hmac-secret debe tener al menos 32 bytes para HS256");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Se siguen validando expiración e emisor, como con los tokens reales
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
