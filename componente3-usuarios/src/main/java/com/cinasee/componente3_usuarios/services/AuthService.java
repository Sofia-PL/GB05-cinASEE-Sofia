package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.LoginRequest;
import com.cinasee.componente3_usuarios.dto.RegisterUserRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    void register(RegisterUserRequest request);
}
