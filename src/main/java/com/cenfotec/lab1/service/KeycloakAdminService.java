package com.cenfotec.lab1.service;

import com.cenfotec.lab1.dto.RegistroRequestDTO;
import com.cenfotec.lab1.dto.UsuarioDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registra y lista usuarios hablando con la API de administración de Keycloak.
 *
 * Los usuarios no están en la base lab1, están en Keycloak. Por eso la API
 * entra como el cliente "inventario-admin" (que tiene secreto) y le pide a Keycloak
 * que cree o liste usuarios. El navegador nunca ve ese secreto.
 */
@Slf4j
@Service
public class KeycloakAdminService {

    // Solo me interesan estos dos roles para mostrar en la página de usuarios
    static final List<String> ROLES_APP = List.of("SUPER-ADMIN-ROLE", "USER");
    // Todo el que se registra queda como USER
    static final String ROL_REGISTRO = "USER";
    private static final int MAX_USUARIOS = 500;

    private final RestClient restClient = RestClient.create();
    private final String tokenUri;
    private final String adminBaseUrl;
    private final String clientId;
    private final String clientSecret;

    // Guardo el token para no pedirle uno nuevo a Keycloak en cada request
    private String tokenActual;
    private Instant venceEn = Instant.EPOCH;

    // Los valores vienen del application.yml (keycloak.token-uri y keycloak.admin.*)
    public KeycloakAdminService(@Value("${keycloak.token-uri}") String tokenUri,
                                @Value("${keycloak.admin.base-url}") String adminBaseUrl,
                                @Value("${keycloak.admin.client-id}") String clientId,
                                @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        this.adminBaseUrl = adminBaseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // ---------------------------------------------------------------- token

    /**
     * Pide el token del cliente inventario-admin (grant_type=client_credentials).
     * Es lo mismo que probé en PowerShell con Invoke-RestMethod.
     * synchronized: si llegan dos requests al mismo tiempo, solo uno pide token nuevo.
     */
    private synchronized String token() {
        // Si todavía sirve, lo reutilizo
        if (tokenActual != null && Instant.now().isBefore(venceEn)) {
            return tokenActual;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse t = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (t == null || t.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak no devolvió token de administración");
        }
        tokenActual = t.accessToken();
        // Dura 900 s; lo doy por vencido 30 s antes para no usarlo justo cuando expira
        venceEn = Instant.now().plusSeconds(Math.max(0, t.expiresIn() - 30));
        return tokenActual;
    }

    // Si Keycloak está apagado o no responde, la API devuelve 503 en vez de un 500 feo
    private ResponseStatusException keycloakNoDisponible(RestClientException e) {
        log.error("Error al comunicarse con Keycloak", e);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo comunicar con Keycloak");
    }

    // ---------------------------------------------------------------- respuestas de Keycloak

    // Keycloak manda más campos (scope, token_type...), solo ocupo estos dos
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("expires_in") long expiresIn) {
    }

    // ---------------------------------------------------------------- registro

    /**
     * Crea el usuario en Keycloak y le asigna el rol USER.
     * Son dos llamadas: 1) crear el usuario, 2) darle el rol.
     * Si la segunda falla, borro el usuario para que no quede uno sin rol.
     */
    public UsuarioDTO registrar(RegistroRequestDTO r) {
        // El usuario como lo pide Keycloak (UserRepresentation)
        Map<String, Object> nuevo = Map.of(
                "username", r.username(),
                "email", r.email(),
                "firstName", r.firstName(),
                "lastName", r.lastName(),
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", r.password(),
                        "temporary", false))   // false: no le pide cambiar la contraseña al entrar
        );

        // 1) Crear el usuario
        String id;
        try {
            ResponseEntity<Void> respuesta = restClient.post()
                    .uri(adminBaseUrl + "/users")
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nuevo)
                    .retrieve()
                    .toBodilessEntity();
            // Keycloak responde 201 sin body; el id viene al final del header Location
            id = idDesdeLocation(respuesta.getHeaders().getLocation());
        } catch (HttpClientErrorException.Conflict e) {
            // 409: ya existe ese username o ese correo
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese nombre de usuario o correo");
        } catch (HttpClientErrorException.BadRequest e) {
            // 400: Keycloak no aceptó algún dato
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak rechazó los datos del usuario");
        } catch (RestClientException e) {
            throw keycloakNoDisponible(e);
        }

        // 2) Darle el rol USER: primero busco el rol y luego se lo asigno
        try {
            RolRepresentation rol = restClient.get()
                    .uri(adminBaseUrl + "/roles/{rol}", ROL_REGISTRO)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(RolRepresentation.class);
            restClient.post()
                    .uri(adminBaseUrl + "/users/{id}/role-mappings/realm", id)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(rol))   // Keycloak pide una lista de roles
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            borrarSinFallar(id);
            throw keycloakNoDisponible(e);
        }

        log.info("Usuario {} registrado con rol {}", r.username(), ROL_REGISTRO);
        return new UsuarioDTO(id, r.username(), r.email(), r.firstName(), r.lastName(),
                true, System.currentTimeMillis(), List.of(ROL_REGISTRO));
    }

    // De ".../users/8f3c-..." me quedo con "8f3c-..."
    static String idDesdeLocation(URI location) {
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak no devolvió el id del usuario creado");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // Deshace el usuario si no se le pudo dar el rol; si esto también falla, solo lo dejo en el log
    private void borrarSinFallar(String id) {
        try {
            restClient.delete()
                    .uri(adminBaseUrl + "/users/{id}", id)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("No se pudo deshacer el usuario {} tras fallar la asignación de rol", id, e);
        }
    }


    // ---------------------------------------------------------------- listado

    /**
     * Lista los usuarios del realm con sus roles.
     * Keycloak no trae los roles junto con el usuario, así que hago:
     * 1 llamada para los usuarios + 1 por cada rol (en vez de 1 por cada usuario).
     * El service account no sale en /users, Keycloak lo esconde solo.
     */
    public List<UsuarioDTO> listar() {
        try {
            List<UsuarioRepresentation> usuarios = restClient.get()
                    .uri(adminBaseUrl + "/users?max={max}", MAX_USUARIOS)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UsuarioRepresentation>>() {});
            if (usuarios == null) {
                return List.of();
            }

            // Para cada rol, guardo los ids de los usuarios que lo tienen
            Map<String, Set<String>> idsPorRol = new HashMap<>();
            for (String rol : ROLES_APP) {
                List<UsuarioRepresentation> conRol = restClient.get()
                        .uri(adminBaseUrl + "/roles/{rol}/users?max={max}", rol, MAX_USUARIOS)
                        .headers(h -> h.setBearerAuth(token()))
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<UsuarioRepresentation>>() {});
                Set<String> ids = new HashSet<>();
                if (conRol != null) {
                    conRol.forEach(u -> ids.add(u.id()));
                }
                idsPorRol.put(rol, ids);
            }

            // Armo el DTO de cada usuario con sus roles y los ordeno por username
            return usuarios.stream()
                    .map(u -> new UsuarioDTO(u.id(), u.username(), u.email(), u.firstName(), u.lastName(),
                            u.enabled(), u.createdTimestamp(), rolesDe(u.id(), idsPorRol)))
                    .sorted(Comparator.comparing(UsuarioDTO::username))
                    .toList();
        } catch (RestClientException e) {
            throw keycloakNoDisponible(e);
        }
    }

    // Revisa en qué roles aparece el id del usuario
    private List<String> rolesDe(String id, Map<String, Set<String>> idsPorRol) {
        List<String> roles = new ArrayList<>();
        for (String rol : ROLES_APP) {
            if (idsPorRol.getOrDefault(rol, Set.of()).contains(id)) {
                roles.add(rol);
            }
        }
        return roles;
    }

    // Para asignar el rol, Keycloak necesita su id y su nombre
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RolRepresentation(String id, String name) {
    }

    // Lo que ocupo de cada usuario que devuelve Keycloak
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsuarioRepresentation(String id, String username, String email, String firstName,
                                        String lastName, boolean enabled, Long createdTimestamp) {
    }
}