package com.ginomarin.persistencia.controllers;

import com.ginomarin.persistencia.dto.LoginRequestDTO;
import com.ginomarin.persistencia.dto.LoginResponseDTO;
import com.ginomarin.persistencia.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login a través de la API. Es público (ver SecurityConfig): no se puede pedir token
 * para obtener un token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }
}
