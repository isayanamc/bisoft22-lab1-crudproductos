package com.cenfotec.lab1.dto;


import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductoDTO (

    //lo que entra y sale por la API para Producto
    Long id,

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    String nombre,

    @Size(max = 255, message = "La descripción no puede tener más de 255 caracteres")
    String descripcion,

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    Integer stock,

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio admite hasta 8 enteros y 2 decimales")
    BigDecimal precio,

    @NotNull(message = "La categoría es obligatoria")
    Long categoriaId,

    String categoriaNombre
){

}