package com.cenfotec.lab1.controllers;

import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.model.Producto;
import com.cenfotec.lab1.repository.CategoriaRepository;
import com.cenfotec.lab1.repository.ProductoRepository;
import com.cenfotec.lab1.security.KeycloakJwtConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional

public class ProductoControllerTest {

    private static final String URL = "/api/v1/productos";
    private static final String CLIENT_ID = "inventario-api";
    private static final KeycloakJwtConverter CONVERTER = new KeycloakJwtConverter(CLIENT_ID);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    // Cada test arranca con una categoría propia (se deshace al final)
    private Categoria bebidas;

    @BeforeEach
    void crearCategoria() {
        bebidas = guardarCategoria("Test-Bebidas");
    }

    // ---------- Sin token y USER ----------

    @Test
    void sinToken_listar_da401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void usuario_listar_da200() throws Exception {
        mvc.perform(get(URL).with(comoUsuario()))
                .andExpect(status().isOk());
    }

    @Test
    void usuario_consultarPorId_traeLaCategoria() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);

        mvc.perform(get(URL + "/" + cafe.getId()).with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Test-Cafe"))
                .andExpect(jsonPath("$.categoriaId").value(bebidas.getId().intValue()))
                .andExpect(jsonPath("$.categoriaNombre").value("Test-Bebidas"));
    }

    @Test
    void usuario_crear_da403() throws Exception {
        mvc.perform(post(URL).with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Te", "1500.00", 10, bebidas.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuario_editar_da403() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);

        mvc.perform(put(URL + "/" + cafe.getId()).with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Otro", "1.00", 1, bebidas.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuario_borrar_da403() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);

        mvc.perform(delete(URL + "/" + cafe.getId()).with(comoUsuario()))
                .andExpect(status().isForbidden());
    }

    // ---------- SUPER-ADMIN-ROLE ----------

    @Test
    void admin_crear_da201ConCategoria() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Te", "1500.00", 10, bebidas.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Test-Te"))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.categoriaNombre").value("Test-Bebidas"));
    }

    @Test
    void admin_editar_cambiaDatosYCategoria() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);
        Categoria snacks = guardarCategoria("Test-Snacks");

        mvc.perform(put(URL + "/" + cafe.getId()).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Galletas", "800.50", 3, snacks.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cafe.getId().intValue()))
                .andExpect(jsonPath("$.nombre").value("Test-Galletas"))
                .andExpect(jsonPath("$.categoriaNombre").value("Test-Snacks"));
    }

    @Test
    void admin_borrar_da204_yLuegoNoExiste() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);

        mvc.perform(delete(URL + "/" + cafe.getId()).with(comoAdmin()))
                .andExpect(status().isNoContent());

        mvc.perform(get(URL + "/" + cafe.getId()).with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    // ---------- Validaciones: 400 y 404 ----------

    @Test
    void admin_crearConCategoriaInexistente_da400() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Te", "1500.00", 10, 999999999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No existe la categoría con id 999999999"));
    }

    @Test
    void admin_crearSinCategoria_da400() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Test-Te", "precio": 1500.00, "stock": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.categoriaId").exists());
    }

    @Test
    void admin_crearConPrecioYStockNegativos_da400() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test-Te", "-5.00", -1, bebidas.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precio").exists())
                .andExpect(jsonPath("$.errores.stock").exists());
    }

    @Test
    void admin_borrarIdInexistente_da404() throws Exception {
        mvc.perform(delete(URL + "/999999999").with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    // ---------- Regla de negocio ----------

    @Test
    void admin_borrarCategoriaConProductos_da409_yNoBorraNada() throws Exception {
        Producto cafe = guardarProducto("Test-Cafe", bebidas);

        mvc.perform(delete("/api/v1/categorias/" + bebidas.getId()).with(comoAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());

        // La categoría y el producto siguen ahí
        assertTrue(categoriaRepository.existsById(bebidas.getId()));
        assertTrue(productoRepository.existsById(cafe.getId()));
    }

    // ---------- Ayudantes ----------

    private static String body(String nombre, String precio, int stock, Long categoriaId) {
        return """
                {"nombre": "%s", "descripcion": "Creado por el test", "precio": %s, "stock": %d, "categoriaId": %d}
                """.formatted(nombre, precio, stock, categoriaId);
    }

    private Categoria guardarCategoria(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categoriaRepository.save(categoria);
    }

    private Producto guardarProducto(String nombre, Categoria categoria) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setPrecio(new BigDecimal("1000.00"));
        producto.setStock(5);
        producto.setCategoria(categoria);
        return productoRepository.save(producto);
    }

    // Mismos roles y permisos que realm-inventario.json le da a cada usuario
    private static RequestPostProcessor comoAdmin() {
        return tokenCon("SUPER-ADMIN-ROLE",
                "categoria:leer", "categoria:escribir", "categoria:eliminar",
                "producto:leer", "producto:escribir", "producto:eliminar");
    }

    private static RequestPostProcessor comoUsuario() {
        return tokenCon("USER", "categoria:leer", "producto:leer");
    }

    private static RequestPostProcessor tokenCon(String rol, String... permisos) {
        return jwt()
                .jwt(token -> token
                        .claim("preferred_username", rol.toLowerCase())
                        .claim("realm_access", Map.of("roles", List.of(rol)))
                        .claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of(permisos)))))
                .authorities(token -> new ArrayList<>(CONVERTER.convert(token).getAuthorities()));
    }
}

