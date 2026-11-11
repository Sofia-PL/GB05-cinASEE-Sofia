package com.cinasee.componente5_gateway.util;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private final JwtDecoder jwtDecoder;

    public JwtUtil(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public Jwt decodeAndValidate(String token) {
        return jwtDecoder.decode(token);
    }
}
