package com.cinasee.componente3_usuarios.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestRateLimiterTest {

    private static final String DEVICE_ID = "web-12345678";
    private static final String KEY = "rate:guest:" + DEVICE_ID;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private GuestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new GuestRateLimiter(redisTemplate, 10, 60);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void firstRequestCreatesCounterWithExpiration() {
        when(valueOperations.increment(KEY)).thenReturn(1L);

        assertDoesNotThrow(() -> rateLimiter.checkLimit(DEVICE_ID));

        verify(redisTemplate).expire(KEY, Duration.ofSeconds(60));
    }

    @Test
    void requestOverLimitReturnsTooManyRequests() {
        when(valueOperations.increment(KEY)).thenReturn(11L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rateLimiter.checkLimit(DEVICE_ID)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }

    @Test
    void redisFailureDoesNotBreakGuestTokenEndpoint() {
        when(valueOperations.increment(KEY))
                .thenThrow(new RedisConnectionFailureException("Redis no disponible"));

        assertDoesNotThrow(() -> rateLimiter.checkLimit(DEVICE_ID));
    }

    @Test
    void blankDeviceIdIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rateLimiter.checkLimit(" ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
