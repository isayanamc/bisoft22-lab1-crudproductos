package com.cenfotec.lab1.mappers;


import com.cenfotec.lab1.dto.CategoriaDTO;
import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.repository.CategoriaRepository;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoriaMapper {
    //MapStruck genera la implementación al compilar (target/generated-sources)
    CategoriaDTO toDto(Categoria categoria);

    List<CategoriaDTO> toDtoList(List<Categoria> categorias);

    Categoria toEntity(CategoriaDTO categoriaDTO);
}
