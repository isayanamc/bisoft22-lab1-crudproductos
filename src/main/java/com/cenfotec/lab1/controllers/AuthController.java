package com.cenfotec.lab1.controllers;

import com.cenfotec.lab1.dto.LoginRequestDTO;
import com.cenfotec.lab1.dto.LoginResponseDTO;
import com.cenfotec.lab1.dto.RegistroRequestDTO;
import com.cenfotec.lab1.dto.UsuarioDTO;
import com.cenfotec.lab1.service.AuthService;
import com.cenfotec.lab1.service.KeycloakAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Login a través de la API. Es público (ver SecurityConfig): no se puede pedir token
 * para obtener un token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KeycloakAdminService keycloakAdminService;

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)   // 201 en vez de 200, porque se creó algo
    public UsuarioDTO registro(@Valid @RequestBody RegistroRequestDTO request) {
        return keycloakAdminService.registrar(request);
    }
}
