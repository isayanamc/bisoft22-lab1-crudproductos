package com.cenfotec.lab1.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class KeycloakJwtConverterTest {

    private final KeycloakJwtConverter converter = new KeycloakJwtConverter("inventario-api");

    @Test
    void rolDeRealm_llevaPrefijoROLE() {
        Jwt jwt = token(Map.of("roles", List.of("SUPER-ADMIN-ROLE")), null);

        assertTrue(authorities(converter.convert(jwt)).contains("ROLE_SUPER-ADMIN-ROLE"));
    }

    @Test
    void permisosDelClient_pasanTalCual() {
        Jwt jwt = token(Map.of("roles", List.of("USER")),
                Map.of("inventario-api", Map.of("roles", List.of("categoria:leer", "producto:leer"))));

        List<String> authorities = authorities(converter.convert(jwt));

        assertTrue(authorities.contains("ROLE_USER"));
        assertTrue(authorities.contains("categoria:leer"));
        assertTrue(authorities.contains("producto:leer"));
        assertEquals(3, authorities.size());
    }

    @Test
    void permisosDeOtroClient_seIgnoran() {
        Jwt jwt = token(Map.of("roles", List.of("USER")),
                Map.of("otra-api", Map.of("roles", List.of("categoria:eliminar"))));

        assertEquals(List.of("ROLE_USER"), authorities(converter.convert(jwt)));
    }

    @Test
    void tokenSinRoles_noTieneAuthorities() {
        Jwt jwt = token(null, null);

        assertTrue(authorities(converter.convert(jwt)).isEmpty());
    }

    @Test
    void elNombreEsElUsername() {
        Jwt jwt = token(Map.of("roles", List.of("USER")), null);

        assertEquals("usuario", converter.convert(jwt).getName());
    }

    private static Jwt token(Map<String, ?> realmAccess, Map<String, ?> resourceAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none")
                .claim("preferred_username", "usuario");
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        if (resourceAccess != null) {
            builder.claim("resource_access", resourceAccess);
        }
        return builder.build();
    }

    private static List<String> authorities(AbstractAuthenticationToken auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
