package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.GuestTokenRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestTokenServiceImpl implements GuestTokenService {

    private final JwtProvider jwtProvider;
    private final GuestRateLimiter guestRateLimiter;

    @Override
    public TokenResponse createGuestToken(GuestTokenRequest request) {
        // TODO: validar request.getCaptchaToken() contra el proveedor de CAPTCHA
        // antes de emitir el token. El rate limiting sí se aplica ya por deviceId.
        guestRateLimiter.checkLimit(request.getDeviceId());

        String guestId = "guest:" + UUID.randomUUID();
        String token = jwtProvider.generateGuestToken(guestId, request.getDeviceId());

        return new TokenResponse(
                token,
                "Bearer",
                Math.toIntExact(jwtProvider.getGuestExpirationSeconds()),
                TokenResponse.RoleEnum.VISITANTE
        );
    }
}
