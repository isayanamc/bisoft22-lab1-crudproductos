package com.cenfotec.lab1.service;

import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.repository.CategoriaRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j

public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<Categoria> findAll() {
        return categoriaRepository.findAll(Sort.by("nombre"));
    }

    @Transactional(readOnly = true)
    public Optional<Categoria> findById(Long id) {
        return categoriaRepository.findById(id);
    }

    @Transactional
    public Categoria create(Categoria categoria) {
        categoria.setId(null); //Sin id para que save() haga INSERT sin sobreescribir {
        if (categoriaRepository.existsByNombreIgnoreCase(categoria.getNombre())) {
            throw nombreRepetido(categoria.getNombre());
        }
        log.info("Creando categoría '{}'", categoria.getNombre());
        return categoriaRepository.save(categoria);
    }

    //devuelve vacío si no existe la categoría (el controller lo convierte en un 404)
    @Transactional
    public Optional<Categoria> update(Long id, Categoria datos) {
        return categoriaRepository.findById(id).map(categoria -> {
            if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(datos.getNombre(), id)) {
                throw nombreRepetido(datos.getNombre());
            }
            categoria.setNombre(datos.getNombre());
            categoria.setDescripcion(datos.getDescripcion());
            log.info("Actualizando categoría {}", id);
            return categoriaRepository.save(categoria);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (!categoriaRepository.existsById(id)) {
            return false;
        }
        categoriaRepository.deleteById(id);
        log.info("Categoría {} eliminada, id");
        return true;
    }

    private ResponseStatusException nombreRepetido(String nombre) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe una categoría con el nombre: " + nombre );
    }
}
