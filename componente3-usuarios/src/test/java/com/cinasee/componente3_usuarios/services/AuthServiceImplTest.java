package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.LoginRequest;
import com.cinasee.componente3_usuarios.dto.RegisterUserRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import com.cinasee.componente3_usuarios.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerCreatesUserWithHashedPasswordAndDefaultRole() {
        RegisterUserRequest request = new RegisterUserRequest(
                "  Cliente@CinAsee.com ",
                "Password123!",
                "María Gómez"
        );
        when(userRepository.existsByEmailIgnoreCase("cliente@cinasee.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("cliente@cinasee.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals("María Gómez", saved.getFullName());
        assertEquals(Role.CLIENTE_REGISTRADO, saved.getRole());
        assertEquals(0, saved.getLoyaltyPoints());
        assertNotNull(saved.getPreferredGenres());
        assertTrue(saved.getPreferredGenres().isEmpty());
    }

    @Test
    void registerThrowsConflictWhenEmailExists() {
        RegisterUserRequest request = new RegisterUserRequest(
                "cliente@cinasee.com",
                "Password123!",
                "María"
        );
        when(userRepository.existsByEmailIgnoreCase("cliente@cinasee.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("cliente@cinasee.com", "Password123!");
        User user = User.builder()
                .id(42L)
                .email("cliente@cinasee.com")
                .passwordHash("hashed")
                .fullName("María")
                .role(Role.CLIENTE_REGISTRADO)
                .loyaltyPoints(0)
                .build();

        when(userRepository.findByEmailIgnoreCase("cliente@cinasee.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed")).thenReturn(true);
        when(jwtProvider.generateToken(42L, "cliente@cinasee.com", Role.CLIENTE_REGISTRADO))
                .thenReturn("jwt-token");
        when(jwtProvider.getExpirationSeconds()).thenReturn(3600L);

        TokenResponse response = authService.login(request);

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
        assertEquals(TokenResponse.RoleEnum.CLIENTE_REGISTRADO, response.getRole());
    }

    @Test
    void loginThrowsUnauthorizedWhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest("nadie@cinasee.com", "Password123!");
        when(userRepository.findByEmailIgnoreCase("nadie@cinasee.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(jwtProvider, never()).generateToken(any(), any(), any());
    }

    @Test
    void loginThrowsUnauthorizedWhenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("cliente@cinasee.com", "WrongPassword");
        User user = User.builder()
                .id(42L)
                .email("cliente@cinasee.com")
                .passwordHash("hashed")
                .fullName("María")
                .role(Role.CLIENTE_REGISTRADO)
                .build();

        when(userRepository.findByEmailIgnoreCase("cliente@cinasee.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed")).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(jwtProvider, never()).generateToken(any(), any(), any());
    }
}
