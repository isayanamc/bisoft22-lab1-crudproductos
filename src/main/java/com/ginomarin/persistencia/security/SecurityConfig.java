package com.ginomarin.persistencia.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración central de seguridad.
 *
 * - La API es un "Resource Server": NO maneja usuarios ni contraseñas.
 *   Solo recibe un token JWT (emitido por Keycloak) y lo valida.
 * - @EnableMethodSecurity habilita @PreAuthorize / @Secured en los controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${keycloak.client-id}") String clientId) throws Exception {
        http
                // API REST sin sesión ni formularios: CSRF no aplica
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 1) Reglas por URL (gruesas). El detalle fino va en anotaciones de los controllers.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/demo/publico", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated()
                )

                // 2) Cada request debe traer "Authorization: Bearer <jwt>"
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter(clientId)))
                );

        return http.build();
    }
}
