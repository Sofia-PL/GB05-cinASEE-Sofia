package es.cineasee.analitica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del Componente 4 como OAuth2 Resource Server.
 *
 * <p>Valida los JWT RS256 que firma el Componente 3 descargando su clave
 * pública del JWKS ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri})
 * y comprobando el claim {@code iss}.</p>
 *
 * <p>Política de acceso:</p>
 * <ul>
 *   <li>{@code /metrics/**} y {@code /reports/**}: solo STAFF_TAQUILLA o ADMIN.</li>
 *   <li>{@code /recommendations/users/{id}}: el propio usuario o STAFF/ADMIN
 *       (se comprueba con {@code @PreAuthorize} en el controlador).</li>
 *   <li>El resto: cualquier JWT válido.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
                // API REST sin estado: no hay cookies de sesión, así que CSRF no aplica
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Datos agregados de negocio: solo personal interno
                        .requestMatchers(HttpMethod.GET, "/metrics/**", "/reports/**")
                        .hasAnyRole("ADMIN", "STAFF_TAQUILLA")
                        // El resto requiere un JWT válido (la propiedad se comprueba en el controlador)
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
