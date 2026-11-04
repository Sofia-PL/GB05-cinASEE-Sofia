package com.cinasee.componente3_usuarios.security;

import com.cinasee.componente3_usuarios.models.Role;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class JwtProvider {

    private final JwtEncoder jwtEncoder;
    private final RSAKey rsaKey;
    private final long expirationSeconds;
    private final long guestExpirationSeconds;
    private final String issuer;

    public JwtProvider(
            JwtEncoder jwtEncoder,
            RSAKey rsaKey,
            @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds,
            @Value("${app.jwt.guest-expiration-seconds:900}") long guestExpirationSeconds,
            @Value("${app.jwt.issuer:http://localhost:8083/v1/iam}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.rsaKey = rsaKey;
        this.expirationSeconds = expirationSeconds;
        this.guestExpirationSeconds = guestExpirationSeconds;
        this.issuer = issuer;
    }

    public String generateToken(Long userId, String email, Role role) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(expirationSeconds, ChronoUnit.SECONDS))
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("role", role.name())
                .build();

        return encode(claims);
    }

    /**
     * Emite un token de corta duración para un invitado no registrado.
     * El token solo concede el rol VISITANTE y no da acceso a datos de usuario.
     */
    public String generateGuestToken(String guestId, String deviceId) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(guestExpirationSeconds, ChronoUnit.SECONDS))
                .subject(guestId)
                .id(UUID.randomUUID().toString())
                .claim("role", Role.VISITANTE.name())
                .claim("guest", true);

        if (deviceId != null && !deviceId.isBlank()) {
            claimsBuilder.claim("device_id", deviceId);
        }

        return encode(claimsBuilder.build());
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public long getGuestExpirationSeconds() {
        return guestExpirationSeconds;
    }

    private String encode(JwtClaimsSet claims) {
        // RS256: se firma con la clave privada; los demás servicios validan con la pública.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKey.getKeyID())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
