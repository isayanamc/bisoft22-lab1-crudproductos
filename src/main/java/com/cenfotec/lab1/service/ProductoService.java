package com.cenfotec.lab1.service;

import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.model.Producto;
import com.cenfotec.lab1.repository.CategoriaRepository;
import com.cenfotec.lab1.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j

public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<Producto> findAll() {
        return productoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Producto> findById(Long id) {
        return productoRepository.findById(id);
    }

    @Transactional
    public Producto create(Producto producto, Long categoriaId) {
        producto.setId(null);
        producto.setCategoria(buscarCategoria(categoriaId));
        log.info("Creando producto '{}' en la categoría {}", producto.getNombre(), categoriaId);
        return productoRepository.save(producto);
    }

    @Transactional
    public Optional<Producto> update(Long id, Producto datos, Long categoriaId) {
        return productoRepository.findById(id).map(producto -> {
            producto.setNombre(datos.getNombre());
            producto.setDescripcion(datos.getDescripcion());
            producto.setPrecio(datos.getPrecio());
            producto.setStock(datos.getStock());
            producto.setCategoria(buscarCategoria(categoriaId));
            log.info("actualizando producto {}", id);
            return productoRepository.save(producto);
        });
    }

    @Transactional
    public boolean delete(Long id){
        if (!productoRepository.existsById(id)) {
            return false;
        }
        productoRepository.deleteById(id);
        log.info("Producto {} eliminado", id);
        return true;
    }

    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe la categoría con id " + categoriaId));
    }

}
