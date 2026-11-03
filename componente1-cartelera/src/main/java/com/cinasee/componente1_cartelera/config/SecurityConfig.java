package com.cinasee.componente1_cartelera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Excepciones de seguridad públicas según el esquema OpenAPI
                .requestMatchers(HttpMethod.GET, "/movies", "/api/v1/movies").permitAll() // Listado de películas público
                .requestMatchers(HttpMethod.GET, "/sessions", "/api/v1/sessions").permitAll() // Consulta de cartelera pública
                // El resto de la API protegida por defecto
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}

