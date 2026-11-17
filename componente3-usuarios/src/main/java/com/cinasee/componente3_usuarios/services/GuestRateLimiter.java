package com.cinasee.componente3_usuarios.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Límite de peticiones por dispositivo para el endpoint público /auth/guest.
 *
 * <p>Usa un contador de ventana fija en Redis: la primera petición de la
 * ventana crea la clave con TTL y las siguientes incrementan el contador. Al
 * superar el máximo se devuelve 429.</p>
 *
 * <p>Si Redis no está disponible se registra un aviso y se permite la petición:
 * el servicio sigue funcionando en desarrollo, pero el límite no se aplica.</p>
 */
@Slf4j
@Component
public class GuestRateLimiter {

    private static final String KEY_PREFIX = "rate:guest:";

    private final StringRedisTemplate redisTemplate;
    private final long maxRequests;
    private final Duration window;

    public GuestRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.guest.rate-limit.max-requests:10}") long maxRequests,
            @Value("${app.guest.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void checkLimit(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El deviceId es obligatorio");
        }

        String key = KEY_PREFIX + deviceId.trim();
        try {
            Long requests = redisTemplate.opsForValue().increment(key);
            if (requests == null) {
                return;
            }
            if (requests == 1L) {
                redisTemplate.expire(key, window);
            }
            if (requests > maxRequests) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiadas solicitudes de token de invitado. Inténtelo más tarde."
                );
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo aplicar el rate limit de invitados en Redis: {}", e.getMessage());
        }
    }
}
