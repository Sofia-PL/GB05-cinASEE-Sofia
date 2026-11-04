package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.LoginRequest;
import com.cinasee.componente3_usuarios.dto.RegisterUserRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import com.cinasee.componente3_usuarios.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional
    public void register(RegisterUserRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo electrónico ya está en uso");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(Role.CLIENTE_REGISTRADO)
                .loyaltyPoints(0)
                .preferredGenres(new ArrayList<>())
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Credenciales inválidas"
                ));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        String token = jwtProvider.generateToken(user.getId(), user.getEmail(), user.getRole());

        return new TokenResponse(
                token,
                "Bearer",
                Math.toIntExact(jwtProvider.getExpirationSeconds()),
                TokenResponse.RoleEnum.valueOf(user.getRole().name())
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
