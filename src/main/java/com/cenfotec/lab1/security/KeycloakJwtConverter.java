package com.cenfotec.lab1.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Keycloak pone la información así dentro del token:
 * "realm_access":    { "roles": ["SUPER-ADMIN-ROLE", ...] }                         -> ROLES
 * "resource_access": { "inventario-api": { "roles": ["producto:leer", ...] } }      -> PERMISOS
 *
 * Los permisos no se asignan directo al usuario: viven dentro de un rol compuesto
 * (SUPER-ADMIN-ROLE o USER) y Keycloak los expande al emitir el token. Por eso en la
 * base de Keycloak cada usuario tiene un solo rol, pero el token trae todos sus permisos.
 *
 * Spring no conoce ese formato, así que lo convertimos:
 * - Rol de realm "SUPER-ADMIN-ROLE"   -> authority "ROLE_SUPER-ADMIN-ROLE" (se usa con hasRole('SUPER-ADMIN-ROLE')))
 * - Rol de cliente "categoria:leer"   -> authority "categoria:leer"         (se usa con hasAuthority('categoria:leer'))
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String clientId;

    public KeycloakJwtConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                roles(jwt).stream().map(rol -> "ROLE_" + rol),
                permisos(jwt).stream()
        ).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();

        // Usamos el username legible ("ana") como nombre del usuario autenticado
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }

    private List<String> roles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        return extraerRoles(realmAccess);
    }

    @SuppressWarnings("unchecked")
    private List<String> permisos(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        return extraerRoles((Map<String, Object>) resourceAccess.get(clientId));
    }

    @SuppressWarnings("unchecked")
    private List<String> extraerRoles(Map<String, Object> claim) {
        if (claim == null || !(claim.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return (List<String>) roles;
    }
}
