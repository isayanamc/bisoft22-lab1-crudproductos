package com.cenfotec.lab1.controllers;

import com.cenfotec.lab1.dto.UsuarioDTO;
import com.cenfotec.lab1.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Usuarios registrados en el sistema
 * Permiso usuario:leer -> solo lo tiene el rol SUPER-ADMIN-ROLE
 * El front también esconde la página, pero la restricción de verdad es esta
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final KeycloakAdminService keycloakAdminService;

    // USER recibe 403 aquí aunque escriba la URL a mano
    @PreAuthorize("hasAuthority('usuario:leer')")
    @GetMapping
    public List<UsuarioDTO> findAll() {
        log.info("Listando usuarios");
        return keycloakAdminService.listar();
    }
}