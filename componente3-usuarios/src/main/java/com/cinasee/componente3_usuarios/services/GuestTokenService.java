package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.GuestTokenRequest;
import com.cinasee.componente3_usuarios.dto.TokenResponse;

public interface GuestTokenService {

    TokenResponse createGuestToken(GuestTokenRequest request);
}
