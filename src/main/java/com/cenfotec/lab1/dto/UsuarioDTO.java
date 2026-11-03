package com.cenfotec.lab1.dto;

import java.util.List;

// Lo que devuelve GET /usuarios. Sale de Keycloak y nunca trae la contraseña
public record UsuarioDTO(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Long createdTimestamp,
        List<String> roles
) {
}
