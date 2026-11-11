package com.cinasee.componente2_reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del Componente 2 como OAuth2 Resource Server.
 *
 * <p>No emite tokens: valida los JWT RS256 que firma el Componente 3 (IAM)
 * descargando su clave pública del JWKS
 * ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}) y comprobando
 * el emisor ({@code issuer-uri}). La validación es local (no se llama al
 * Componente 3 en cada petición, solo para descargar/rotar la clave).</p>
 *
 * <p>Roles: se leen del claim "role" del token, con prefijo ROLE_, igual que en
 * el Componente 3: VISITANTE, CLIENTE_REGISTRADO, STAFF_TAQUILLA, ADMIN.</p>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // API REST sin estado: no hay cookies de sesión, así que CSRF no aplica
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El mapa de butacas ocupadas es público (como la cartelera del Comp. 1)
                        .requestMatchers(HttpMethod.GET, "/reservations/shows/*/seats").permitAll()
                        // Vuelta del navegador desde Redsys: sin JWT; la protege la firma de Redsys
                        .requestMatchers("/reservations/payments/redsys/return").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Datos agregados para Analítica (Comp. 4): solo personal interno
                        .requestMatchers(HttpMethod.GET, "/reservations/stats", "/reservations/box-office-data")
                        .hasAnyRole("ADMIN", "STAFF_TAQUILLA")
                        // Portal de validación en puerta: solo personal de sala
                        .requestMatchers(HttpMethod.POST, "/reservations/tickets/*/check-in")
                        .hasAnyRole("ADMIN", "STAFF_TAQUILLA")
                        // Todo lo demás (reservar, pagar, consultar, cancelar) exige un JWT válido,
                        // también el de invitado (VISITANTE); la propiedad se comprueba en el servicio.
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /** Traduce el claim "role" del JWT a autoridades de Spring Security (ROLE_xxx). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
