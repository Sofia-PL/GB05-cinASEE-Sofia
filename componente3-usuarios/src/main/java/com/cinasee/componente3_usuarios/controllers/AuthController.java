package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.dto.GuestTokenRequest;
import com.cinasee.componente3_usuarios.dto.LoginRequest;
import com.cinasee.componente3_usuarios.dto.RegisterUserRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.services.AuthService;
import com.cinasee.componente3_usuarios.services.GuestTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final GuestTokenService guestTokenService;

    @Override
    public ResponseEntity<TokenResponse> loginUser(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @Override
    public ResponseEntity<Void> registerUser(RegisterUserRequest registerUserRequest) {
        authService.register(registerUserRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<TokenResponse> createGuestToken(GuestTokenRequest guestTokenRequest) {
        return ResponseEntity.ok(guestTokenService.createGuestToken(guestTokenRequest));
    }
}
