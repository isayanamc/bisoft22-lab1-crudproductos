package com.cenfotec.lab1.controllers;

import com.cenfotec.lab1.dto.ProductoDTO;
import com.cenfotec.lab1.mappers.ProductoMapper;
import com.cenfotec.lab1.model.Producto;
import com.cenfotec.lab1.service.ProductoService;
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
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Slf4j

public class ProductoController {

    private final ProductoService productoService;
    private final ProductoMapper productoMapper;

    @PreAuthorize("hasAuthority('producto:leer')")
    @GetMapping
    public List<ProductoDTO> findAll() {
        log.info("Listando productos");
        return productoMapper.toDtoList(productoService.findAll());
    }

    @PreAuthorize("hasAuthority('producto:leer')")
    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> findById(@PathVariable Long id) {
        log.info("Buscando producto {}", id);
        return ResponseEntity.of(productoService.findById(id).map(productoMapper::toDto));
    }

    @PreAuthorize("hasAuthority('producto:escribir')")
    @PostMapping
    public ResponseEntity<ProductoDTO> create(@Valid @RequestBody ProductoDTO productoDTO) {
        Producto creado = productoService.create(productoMapper.toEntity(productoDTO), productoDTO.categoriaId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(productoMapper.toDto(creado));
    }

    @PreAuthorize("hasAuthority('producto:escribir')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> update(@PathVariable Long id, @Valid @RequestBody ProductoDTO productoDTO) {
        return ResponseEntity.of(productoService.update(id, productoMapper.toEntity(productoDTO), productoDTO.categoriaId()).map(productoMapper::toDto));
    }

    @PreAuthorize("hasAuthority('producto:eliminar')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        return productoService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
        }
}
