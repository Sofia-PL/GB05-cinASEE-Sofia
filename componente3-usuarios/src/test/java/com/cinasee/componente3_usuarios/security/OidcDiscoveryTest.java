package com.cinasee.componente3_usuarios.security;

import com.cinasee.componente3_usuarios.controllers.WellKnownController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OidcDiscoveryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void otherServicesCanDiscoverJwksAndValidateTokens() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("discovery-test-key")
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/v1/iam";
        WellKnownController controller = new WellKnownController(rsaKey, issuer);

        server.createContext("/v1/iam/.well-known/openid-configuration", exchange -> {
            byte[] body = objectMapper.writeValueAsBytes(controller.openidConfiguration());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.createContext("/v1/iam/.well-known/jwks.json", exchange -> {
            byte[] body = objectMapper.writeValueAsBytes(controller.jwks());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();
        try {
            // Equivalente a lo que hace el Componente 1 con issuer-uri
            JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);

            JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
            JwtProvider jwtProvider = new JwtProvider(encoder, rsaKey, 3600, 900, issuer);
            String token = jwtProvider.generateGuestToken("guest:discovery", "device-1");

            Jwt jwt = decoder.decode(token);
            assertEquals("guest:discovery", jwt.getSubject());
            assertEquals("VISITANTE", jwt.getClaimAsString("role"));
            assertEquals(issuer, jwt.getClaimAsString("iss"));
        } finally {
            server.stop(0);
        }
    }
}
