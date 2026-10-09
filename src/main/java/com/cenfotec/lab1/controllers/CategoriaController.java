package com.cenfotec.lab1.controllers;

/**
 * CRUD de Categoría
 * Permisos de rol
 * categoria:leer
 * categoria:escribir
 * categoria:eliminar
 */


import com.cenfotec.lab1.dto.CategoriaDTO;
import com.cenfotec.lab1.mappers.CategoriaMapper;
import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
@Slf4j

public class CategoriaController {

    private final CategoriaService categoriaService;
    private final CategoriaMapper categoriaMapper;


    @PreAuthorize("hasAuthority('categoria:leer')")
    @GetMapping
    public List<CategoriaDTO> findAll() {
        log.info("Listando categorías");
        return categoriaMapper.toDtoList(categoriaService.findAll());
    }

    @PreAuthorize("hasAuthority('categoria:leer')")
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaDTO> findBy(@PathVariable Long id) {
        return ResponseEntity.of(categoriaService.findById(id).map(categoriaMapper::toDto));
    }

    @PreAuthorize("hasAuthority('categoria:escribir')")
    @PostMapping
    public ResponseEntity<CategoriaDTO> create(@Valid @RequestBody CategoriaDTO categoriaDTO) {
        Categoria creada = categoriaService.create(categoriaMapper.toEntity(categoriaDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creada.getId())
                .toUri();
        return ResponseEntity.created(location).body(categoriaMapper.toDto(creada));
    }

    @PreAuthorize("hasAuthority('categoria:escribir')")
    @PutMapping("/{id}")
    public ResponseEntity<CategoriaDTO> upate(@PathVariable Long id,
            @Valid @RequestBody CategoriaDTO categoriaDTO) {
        return ResponseEntity.of(categoriaService.update(id, categoriaMapper.toEntity(categoriaDTO))
                .map(categoriaMapper::toDto));
    }

    @PreAuthorize("hasAuthority('categoria:eliminar')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return categoriaService.delete(id)
                ? ResponseEntity.noContent().build()            //204
                : ResponseEntity.notFound().build();            //404
    }

}