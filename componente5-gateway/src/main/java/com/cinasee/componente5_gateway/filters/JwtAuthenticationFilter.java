package com.cinasee.componente5_gateway.filters;

import com.cinasee.componente5_gateway.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":\"unauthorized\",\"message\":\"A valid Bearer token is required\"}";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> ROUTED_PATHS = List.of(
            "/api/v1/cinemas/**",
            "/api/v1/movies/**",
            "/api/v1/rooms/**",
            "/api/v1/sessions/**",
            "/v1/reservations/**",
            "/v1/iam/**",
            "/api/metrics/**",
            "/api/reports/**",
            "/api/recommendations/**"
    );

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method) || !isRoutedPath(path) || isPublicPath(method, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(request);
        if (token == null) {
            writeUnauthorized(response);
            return;
        }

        try {
            jwtUtil.decodeAndValidate(token);
        } catch (JwtException exception) {
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRoutedPath(String path) {
        return ROUTED_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private boolean isPublicPath(String method, String path) {
        if ("GET".equalsIgnoreCase(method)
                && (PATH_MATCHER.match("/api/v1/cinemas/**", path)
                || PATH_MATCHER.match("/api/v1/movies/**", path)
                || "/api/v1/sessions".equals(path)
                || PATH_MATCHER.match("/v1/reservations/shows/*/seats", path)
                || PATH_MATCHER.match("/v1/iam/.well-known/**", path)
                || "/v1/iam/actuator/health".equals(path))) {
            return true;
        }

        return PATH_MATCHER.match("/v1/iam/auth/login", path)
                || PATH_MATCHER.match("/v1/iam/auth/register", path)
                || PATH_MATCHER.match("/v1/iam/auth/guest", path);
    }

    private String extractBearerToken(HttpServletRequest request) {
        Enumeration<String> authorizationHeaders = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || !authorizationHeaders.hasMoreElements()) {
            return null;
        }

        String authorization = authorizationHeaders.nextElement();
        if (authorizationHeaders.hasMoreElements()
                || authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
