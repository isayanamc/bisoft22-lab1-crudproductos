package com.cenfotec.lab1.controllers;

import com.cenfotec.lab1.model.Categoria;
import com.cenfotec.lab1.repository.CategoriaRepository;
import com.cenfotec.lab1.security.KeycloakJwtConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del CRUD de Categoría con la seguridad real.
 *
 * Corre contra la MariaDB real sin mocks
 * @Transactional: cada test se deshace al terminar
 * El token se simula con jwt() con los mismos claims que pone Keycloak, y los roles los traduce
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaControllerTest {

    private static final String URL = "/api/v1/categorias";
    private static final String CLIENT_ID = "inventario-api";
    private static final KeycloakJwtConverter CONVERTER = new KeycloakJwtConverter(CLIENT_ID);

    private static final String BODY_VALIDO = """
            {"nombre": "Test-Bebidas", "descripcion": "Frías y calientes"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void sinToken_listar_da401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_crear_da401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void usuario_listar_da200() throws Exception {
        mvc.perform(get(URL).with(comoUsuario()))
                .andExpect(status().isOk());
    }

    @Test
    void usuario_consultarPorId_da200() throws Exception {
        Categoria guardada = guardar("Test-Lectura");

        mvc.perform(get(URL + "/" + guardada.getId()).with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Test-Lectura"));
    }

    @Test
    void usuario_crear_da403() throws Exception {
        mvc.perform(post(URL).with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuario_editar_da403() throws Exception {
        Categoria guardada = guardar("Test-NoEditable");

        mvc.perform(put(URL + "/" + guardada.getId()).with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuario_borrar_da403() throws Exception {
        Categoria guardada = guardar("Test-NoBorrable");

        mvc.perform(delete(URL + "/" + guardada.getId()).with(comoUsuario()))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_crear_da201ConLocation() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Test-Bebidas"));
    }

    @Test
    void admin_editar_da200() throws Exception {
        Categoria guardada = guardar("Test-Viejo");

        mvc.perform(put(URL + "/" + guardada.getId()).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Test-Nuevo", "descripcion": "Editada"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guardada.getId().intValue()))
                .andExpect(jsonPath("$.nombre").value("Test-Nuevo"));
    }

    @Test
    void admin_borrar_da204_yLuegoNoExiste() throws Exception {
        Categoria guardada = guardar("Test-Borrar");

        mvc.perform(delete(URL + "/" + guardada.getId()).with(comoAdmin()))
                .andExpect(status().isNoContent());

        mvc.perform(get(URL + "/" + guardada.getId()).with(comoAdmin()))
                .andExpect(status().isNotFound());
    }


    @Test
    void admin_crearSinNombre_da400() throws Exception {
        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "", "descripcion": "Sin nombre"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void admin_crearNombreRepetido_da409() throws Exception {
        guardar("Test-Bebidas");

        mvc.perform(post(URL).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void consultarIdInexistente_da404() throws Exception {
        mvc.perform(get(URL + "/999999999").with(comoUsuario()))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_editarIdInexistente_da404() throws Exception {
        mvc.perform(put(URL + "/999999999").with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_borrarIdInexistente_da404() throws Exception {
        mvc.perform(delete(URL + "/999999999").with(comoAdmin()))
                .andExpect(status().isNotFound());
    }


    private Categoria guardar(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setDescripcion("Creada por el test");
        return categoriaRepository.save(categoria);
    }

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
