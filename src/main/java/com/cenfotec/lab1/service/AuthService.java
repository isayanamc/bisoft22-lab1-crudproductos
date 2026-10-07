package com.cenfotec.lab1.service;

import com.cenfotec.lab1.dto.LoginRequestDTO;
import com.cenfotec.lab1.dto.LoginResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hace login contra Keycloak en nombre del usuario.
 *
 * La API NO valida la contraseña: solo la reenvía al endpoint de token de Keycloak
 * (grant_type=password) y devuelve el JWT que Keycloak emite.
 */
@Service
public class AuthService {

    private final RestClient restClient = RestClient.create();
    private final String tokenUri;
    private final String clientId;

    public AuthService(@Value("${keycloak.token-uri}") String tokenUri,
                       @Value("${keycloak.client-id}") String clientId) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", request.username());
        form.add("password", request.password());

        try {
            return restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(LoginResponseDTO.class);
        } catch (HttpClientErrorException e) {
            // Keycloak responde 401 con credenciales inválidas (y 400 si la cuenta no está lista)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }
    }
}
