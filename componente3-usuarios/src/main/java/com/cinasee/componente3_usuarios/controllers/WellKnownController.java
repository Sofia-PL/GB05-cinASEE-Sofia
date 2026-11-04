package com.cinasee.componente3_usuarios.controllers;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Expone los metadatos que necesitan los demás microservicios para validar
 * los JWT emitidos por este componente.
 *
 * Con el context-path /v1/iam, las URLs públicas quedan:
 *  - http://localhost:8083/v1/iam/.well-known/openid-configuration
 *  - http://localhost:8083/v1/iam/.well-known/jwks.json
 */
@RestController
public class WellKnownController {

    private final RSAKey rsaKey;
    private final String issuer;

    public WellKnownController(
            RSAKey rsaKey,
            @Value("${app.jwt.issuer:http://localhost:8083/v1/iam}") String issuer
    ) {
        this.rsaKey = rsaKey;
        this.issuer = issuer.endsWith("/")
                ? issuer.substring(0, issuer.length() - 1)
                : issuer;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // toPublicJWKSet() elimina la clave privada: solo se publica la parte pública.
        return new JWKSet(rsaKey).toPublicJWKSet().toJSONObject();
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openidConfiguration() {
        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/.well-known/jwks.json",
                "id_token_signing_alg_values_supported", List.of("RS256")
        );
    }
}
