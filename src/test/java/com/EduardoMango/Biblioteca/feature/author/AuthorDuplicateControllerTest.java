package com.EduardoMango.Biblioteca.feature.author;

import com.EduardoMango.Biblioteca.feature.author.dto.AuthorRequest;
import com.EduardoMango.Biblioteca.feature.author.service.AuthorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:author-duplicate-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.scheduler.loan-reminder.cron=-"
})
class AuthorDuplicateControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AuthorService service;
    @Autowired private AuthorRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void duplicateCreateReturnsConflictDespiteCaseAndWhitespace() throws Exception {
        String last = UUID.randomUUID().toString();
        var created = service.createAuthor(request("Ana Maria", last, "1980-05-12"));
        mockMvc.perform(post("/api/autores").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("  ANA   MARIA  ", last.toUpperCase(), "1980-05-12"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(AuthorIdentity.DUPLICATE_MESSAGE));
        assertThat(repository.findByPublicId(created.publicId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void missingDatesAlsoPreventDuplicates() throws Exception {
        String last = UUID.randomUUID().toString();
        service.createAuthor(request("Lucia", last, null));
        mockMvc.perform(post("/api/autores").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("lucia", last, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void sameNamesWithDifferentKnownDatesAreAllowed() {
        String last = UUID.randomUUID().toString();
        var first = service.createAuthor(request("Juan", last, "1960-01-01"));
        var second = service.createAuthor(request("Juan", last, "1990-01-01"));
        assertThat(first.publicId()).isNotEqualTo(second.publicId());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void editingTheSameAuthorIsAllowedButCollidingWithAnotherIsNot() throws Exception {
        String last = UUID.randomUUID().toString();
        var original = service.createAuthor(request("Ana", last, "1980-01-01"));
        var other = service.createAuthor(request("Eva", last, "1980-01-01"));
        mockMvc.perform(put("/api/autores/" + original.publicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(" ANA ", last, "1980-01-01"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/autores/" + other.publicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("Ana", last, "1980-01-01"))))
                .andExpect(status().isConflict());
        assertThat(repository.findByPublicId(other.publicId()).orElseThrow().getNombre()).isEqualTo("Eva");
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void authorsCreatedBeforeTheIdentityColumnAreStillChecked() throws Exception {
        String last = UUID.randomUUID().toString();
        jdbc.update("insert into authors (public_id,nombre,apellido) values (?,?,?)",
                UUID.randomUUID(), "  Ana   Maria ", last);
        mockMvc.perform(post("/api/autores").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("ana maria", last, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void databaseAllowsOnlyOneOfTwoSimultaneousEquivalentInserts() throws Exception {
        String last = UUID.randomUUID().toString();
        long before = repository.count();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertAtTheSameTime("Ana  Maria", last, barrier));
            var second = executor.submit(() -> insertAtTheSameTime(" ANA MARIA ", last.toUpperCase(), barrier));
            List<Boolean> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.count()).isEqualTo(before + 1);
    }

    @Test
    @WithMockUser(roles = "SOCIO")
    void memberCannotCreateOrEditAuthors() throws Exception {
        mockMvc.perform(post("/api/autores").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("Ana", "Perez", null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/autores/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("Ana", "Perez", null))))
                .andExpect(status().isForbidden());
    }

    private boolean insertAtTheSameTime(String first, String last, CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
                    repository.saveAndFlush(Author.builder().nombre(first).apellido(last).build()));
            return true;
        } catch (DataIntegrityViolationException error) {
            assertThat(AuthorIdentity.isDuplicateConstraint(error)).isTrue();
            return false;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private AuthorRequest request(String first, String last, String date) {
        return new AuthorRequest(first, last, "Argentina", date == null ? null : LocalDate.parse(date));
    }
}
