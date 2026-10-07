package com.cenfotec.lab1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del endpoint de token de Keycloak. Keycloak usa snake_case,
 * por eso mapeamos cada campo con @JsonProperty.
 */
public record LoginResponseDTO(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
