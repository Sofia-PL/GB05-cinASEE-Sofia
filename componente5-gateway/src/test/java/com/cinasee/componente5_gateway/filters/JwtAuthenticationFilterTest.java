package com.cinasee.componente5_gateway.filters;

import com.cinasee.componente5_gateway.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JwtAuthenticationFilterTest {

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertNull(chain.request);
    }

    @Test
    void protectedRouteWithValidTokenContinuesToGateway() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/reservations");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.request);
        assertEquals(200, response.getStatus());
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/recommendations/users/184");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.request);
    }

    @Test
    void publicCatalogAndLoginRoutesDoNotRequireToken() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest catalogRequest = new MockHttpServletRequest("GET", "/api/v1/movies");
        MockHttpServletResponse catalogResponse = new MockHttpServletResponse();
        RecordingFilterChain catalogChain = new RecordingFilterChain();

        filter.doFilter(catalogRequest, catalogResponse, catalogChain);

        assertSame(catalogRequest, catalogChain.request);

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/v1/iam/auth/login");
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        RecordingFilterChain loginChain = new RecordingFilterChain();

        filter.doFilter(loginRequest, loginResponse, loginChain);

        assertSame(loginRequest, loginChain.request);
    }

    @Test
    void cinemaReadsArePublicButWritesRequireAuthentication() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest readRequest = new MockHttpServletRequest("GET", "/api/v1/cinemas/7/rooms");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        RecordingFilterChain readChain = new RecordingFilterChain();

        filter.doFilter(readRequest, readResponse, readChain);

        assertSame(readRequest, readChain.request);

        MockHttpServletRequest writeRequest = new MockHttpServletRequest("POST", "/api/v1/cinemas");
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        RecordingFilterChain writeChain = new RecordingFilterChain();

        filter.doFilter(writeRequest, writeResponse, writeChain);

        assertEquals(401, writeResponse.getStatus());
        assertNull(writeChain.request);
    }

    @Test
    void optionsRequestPassesWithoutToken() throws Exception {
        JwtAuthenticationFilter filter = newFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.request);
    }

    private JwtAuthenticationFilter newFilter() {
        JwtDecoder decoder = token -> {
            if (!"valid-token".equals(token)) {
                throw new JwtException("Invalid token");
            }

            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("184")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .build();
        };
        return new JwtAuthenticationFilter(new JwtUtil(decoder));
    }

    private static final class RecordingFilterChain implements FilterChain {
        private ServletRequest request;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.request = request;
        }
    }
}
