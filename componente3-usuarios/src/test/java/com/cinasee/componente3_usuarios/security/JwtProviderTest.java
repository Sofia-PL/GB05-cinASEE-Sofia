package com.cinasee.componente3_usuarios.security;

import com.cinasee.componente3_usuarios.controllers.WellKnownController;
import com.cinasee.componente3_usuarios.models.Role;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    private static final String ISSUER = "http://localhost:8083/v1/iam";
    private static final String KEY_ID = "test-key";

    private JwtProvider jwtProvider;
    private JwtDecoder jwtDecoder;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();

        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        jwtDecoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        jwtProvider = new JwtProvider(encoder, rsaKey, 3600, 900, ISSUER);
    }

    @Test
    void generatesGuestTokenWithShortTtlAndVisitorRole() {
        String token = jwtProvider.generateGuestToken("guest:123", "device-abc");
        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("guest:123", jwt.getSubject());
        assertEquals("VISITANTE", jwt.getClaimAsString("role"));
        assertEquals(Boolean.TRUE, jwt.getClaim("guest"));
        assertEquals("device-abc", jwt.getClaimAsString("device_id"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertNotNull(jwt.getId());
        assertTrue(jwt.getSubject().startsWith("guest:"));

        // Firma asimétrica: el token debe llevar alg=RS256 y el kid de la clave pública
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals(KEY_ID, jwt.getHeaders().get("kid"));

        long ttlSeconds = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds();
        assertEquals(900, ttlSeconds);
    }

    @Test
    void generatesUserTokenWithEmailAndRole() {
        String token = jwtProvider.generateToken(42L, "cliente@cinasee.com", Role.CLIENTE_REGISTRADO);
        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("42", jwt.getSubject());
        assertEquals("cliente@cinasee.com", jwt.getClaimAsString("email"));
        assertEquals("CLIENTE_REGISTRADO", jwt.getClaimAsString("role"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertEquals("RS256", jwt.getHeaders().get("alg"));

        long ttlSeconds = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds();
        assertEquals(3600, ttlSeconds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void jwksOnlyExposesPublicKeyMaterial() {
        WellKnownController controller = new WellKnownController(rsaKey, ISSUER);
        Map<String, Object> jwks = controller.jwks();

        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertNotNull(keys);
        assertEquals(1, keys.size());

        Map<String, Object> publicKey = keys.get(0);
        assertEquals("RSA", publicKey.get("kty"));
        assertEquals(KEY_ID, publicKey.get("kid"));
        assertEquals("RS256", publicKey.get("alg"));
        assertFalse(publicKey.containsKey("d"), "La clave privada no debe publicarse en el JWKS");
    }

    @Test
    void discoveryDocumentPointsToJwks() {
        WellKnownController controller = new WellKnownController(rsaKey, ISSUER);
        Map<String, Object> configuration = controller.openidConfiguration();

        assertEquals(ISSUER, configuration.get("issuer"));
        assertEquals(ISSUER + "/.well-known/jwks.json", configuration.get("jwks_uri"));
    }
}
