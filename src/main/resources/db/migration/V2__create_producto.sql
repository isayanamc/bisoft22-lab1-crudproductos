-- Cada producto pertenece a una categoría
-- Una categoría puede tener muchos productos
CREATE TABLE producto (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL ,
    descripcion VARCHAR(255),
    precio DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL ,
    categoria_id BIGINT NOT NULL ,

    -- Sin ON DELETE CASCADE: no se puede borrar una categoría que tenga productos
    CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id),
    CONSTRAINT ck_producto_precio CHECK ( precio >= 0 ),
    CONSTRAINT ck_producto_stock CHECK ( stock >= 0 )
);