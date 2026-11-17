package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.GuestTokenRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestTokenServiceImplTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GuestRateLimiter guestRateLimiter;

    @InjectMocks
    private GuestTokenServiceImpl guestTokenService;

    @Test
    void createGuestTokenChecksRateLimitBeforeIssuingToken() {
        GuestTokenRequest request = new GuestTokenRequest("web-12345678");
        when(jwtProvider.generateGuestToken(anyString(), eq("web-12345678"))).thenReturn("guest-jwt");
        when(jwtProvider.getGuestExpirationSeconds()).thenReturn(900L);

        TokenResponse response = guestTokenService.createGuestToken(request);

        verify(guestRateLimiter).checkLimit("web-12345678");
        assertEquals("guest-jwt", response.getAccessToken());
        assertEquals(900, response.getExpiresIn());
        assertEquals(TokenResponse.RoleEnum.VISITANTE, response.getRole());
    }
}
