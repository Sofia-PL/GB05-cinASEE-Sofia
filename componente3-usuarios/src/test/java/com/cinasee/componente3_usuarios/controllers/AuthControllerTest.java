package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.dto.GuestTokenRequest;
import com.cinasee.componente3_usuarios.dto.LoginRequest;
import com.cinasee.componente3_usuarios.dto.RegisterUserRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.services.AuthService;
import com.cinasee.componente3_usuarios.services.GuestTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private GuestTokenService guestTokenService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginUserReturnsTokenResponse() {
        LoginRequest request = new LoginRequest("cliente@cinasee.com", "Password123!");
        TokenResponse token = new TokenResponse(
                "jwt",
                "Bearer",
                3600,
                TokenResponse.RoleEnum.CLIENTE_REGISTRADO
        );
        when(authService.login(request)).thenReturn(token);

        ResponseEntity<TokenResponse> response = authController.loginUser(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(token, response.getBody());
    }

    @Test
    void registerUserReturnsCreated() {
        RegisterUserRequest request = new RegisterUserRequest(
                "cliente@cinasee.com",
                "Password123!",
                "María Gómez"
        );

        ResponseEntity<Void> response = authController.registerUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(authService).register(request);
    }

    @Test
    void createGuestTokenReturnsVisitorToken() {
        GuestTokenRequest request = new GuestTokenRequest("web-12345678");
        TokenResponse token = new TokenResponse(
                "guest-jwt",
                "Bearer",
                900,
                TokenResponse.RoleEnum.VISITANTE
        );
        when(guestTokenService.createGuestToken(request)).thenReturn(token);

        ResponseEntity<TokenResponse> response = authController.createGuestToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(token, response.getBody());
    }
}
