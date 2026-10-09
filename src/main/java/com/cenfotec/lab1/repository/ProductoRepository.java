package com.cenfotec.lab1.repository;

import com.cenfotec.lab1.model.Producto;
import org.hibernate.annotations.DialectOverride;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    //Traer la categoría en la misma consulta
    @Override
    @EntityGraph(attributePaths = "categoria")
    List<Producto>findAll();

    @Override
    @EntityGraph(attributePaths = "categoria")
    Optional<Producto> findById(Long id);

    long countByCategoriaId(Long categoriaId);


}
