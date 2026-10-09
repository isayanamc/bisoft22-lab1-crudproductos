package com.cenfotec.lab1.mappers;

import com.cenfotec.lab1.dto.ProductoDTO;
import com.cenfotec.lab1.model.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)

public interface ProductoMapper {

    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "categoriaNombre", source = "categoria.nombre")
    ProductoDTO toDto(Producto producto);

    List<ProductoDTO> toDtoList(List<Producto> productos);

    @Mapping(target = "categoria", ignore = true)
    Producto toEntity(ProductoDTO productoDTO);
}
