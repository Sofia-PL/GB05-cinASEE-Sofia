package com.cinasee.componente5_gateway.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtUtilTest {

    @Test
    void decodeAndValidateReturnsDecodedJwt() {
        JwtDecoder decoder = token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("184")
                .build();
        JwtUtil jwtUtil = new JwtUtil(decoder);

        Jwt jwt = jwtUtil.decodeAndValidate("test-token");

        assertEquals("184", jwt.getSubject());
        assertEquals("test-token", jwt.getTokenValue());
    }
}
