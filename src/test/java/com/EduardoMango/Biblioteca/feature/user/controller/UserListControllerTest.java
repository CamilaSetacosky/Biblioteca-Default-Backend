package com.EduardoMango.Biblioteca.feature.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-list-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.scheduler.loan-reminder.cron=-"
})
class UserListControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousUserCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SOCIO")
    void memberCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void librarianReceivesPaginatedPublicData() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("size", "1").param("sort", "nombre,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre").value("Juan"))
                .andExpect(jsonPath("$.content[0].publicId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].apellido").exists())
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].rol").exists())
                .andExpect(jsonPath("$.content[0].activo").exists())
                .andExpect(jsonPath("$.content[0].*", hasSize(6)))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void nextPageUsesRequestedOrder() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "1").param("size", "1")
                        .param("sort", "nombre,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Laura"))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void pageBeyondResultsReturnsEmptyContent() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc"})
    @WithMockUser(roles = "BIBLIOTECARIO")
    void invalidPageIsRejected(String page) throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", page))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101"})
    @WithMockUser(roles = "BIBLIOTECARIO")
    void invalidSizeIsRejected(String size) throws Exception {
        mockMvc.perform(get("/api/usuarios").param("size", size))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password,asc", "id,asc", "nombre,wrong", "nombre", "nombre,asc,extra"})
    @WithMockUser(roles = "BIBLIOTECARIO")
    void invalidSortIsRejected(String sort) throws Exception {
        mockMvc.perform(get("/api/usuarios").param("sort", sort))
                .andExpect(status().isBadRequest());
    }
}