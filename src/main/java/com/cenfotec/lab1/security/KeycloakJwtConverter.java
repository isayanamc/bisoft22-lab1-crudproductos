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
 * Traduce el JWT de Keycloak a un Authentication de Spring Security.
 *
 * Keycloak pone la información así dentro del token:
 * <pre>
 * "realm_access":    { "roles": ["ADMIN"] }                          -> ROLES
 * "resource_access": { "persistencia-api": { "roles": ["perro:leer"] } } -> PERMISOS
 * </pre>
 *
 * Spring no conoce ese formato, así que lo convertimos:
 * - Rol de realm "ADMIN"            -> authority "ROLE_ADMIN"  (se usa con hasRole('ADMIN'))
 * - Rol de cliente "perro:leer"     -> authority "perro:leer"  (se usa con hasAuthority('perro:leer'))
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
