package com.EduardoMango.Biblioteca.feature.book;

import com.EduardoMango.Biblioteca.domain.book.dto.ExternalBookDto;
import com.EduardoMango.Biblioteca.domain.book.port.out.ExternalBookSearchPort;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException.Failure;
import com.EduardoMango.Biblioteca.feature.book.repository.BookRepository;
import com.EduardoMango.Biblioteca.feature.author.AuthorRepository;
import com.EduardoMango.Biblioteca.feature.category.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("google-books-errors-test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:google-books-errors-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
class GoogleBooksErrorIntegrationTest {
    private static final String ISBN = "9780134494166";
    private static final String PATH = "/api/v1/books/external/isbn/" + ISBN;
    @Autowired private WebApplicationContext context;
    @Autowired private BookRepository books;
    @Autowired private AuthorRepository authors;
    @Autowired private CategoryRepository categories;
    @MockitoBean private ExternalBookSearchPort provider;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static Stream<Arguments> roleAndFailure() {
        return Stream.of("SOCIO", "BIBLIOTECARIO").flatMap(role ->
                Stream.of(Failure.values()).map(failure -> Arguments.of(role, failure)));
    }

    @ParameterizedTest
    @MethodSource("roleAndFailure")
    void reportsProviderErrorsToBothRolesWithoutPersisting(String role, Failure failure) throws Exception {
        long bookCount = books.count(), authorCount = authors.count(), categoryCount = categories.count();
        when(provider.findByIsbn(ISBN)).thenReturn(Mono.error(new GoogleBooksException(failure, 503, "60")));
        MvcResult pending = mvc.perform(get(PATH).with(user("test-reader").roles(role)))
                .andExpect(request().asyncStarted()).andReturn();
        var result = mvc.perform(asyncDispatch(pending))
                .andExpect(status().is(failure.status()))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(failure.status()))
                .andExpect(jsonPath("$.code").value(failure.code()))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(PATH));
        if (failure.status() == 503) result.andExpect(header().string("Retry-After", "60"));
        else result.andExpect(header().doesNotExist("Retry-After"));
        verify(provider, times(1)).findByIsbn(ISBN);
        assertEquals(bookCount, books.count());
        assertEquals(authorCount, authors.count());
        assertEquals(categoryCount, categories.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOCIO", "BIBLIOTECARIO"})
    void noResultsRemain404(String role) throws Exception {
        when(provider.findByIsbn(ISBN)).thenReturn(Mono.empty());
        MvcResult pending = mvc.perform(get(PATH).with(user("test-reader").roles(role)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isNotFound());
        verify(provider, times(1)).findByIsbn(ISBN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOCIO", "BIBLIOTECARIO"})
    void successStillReturnsPreview(String role) throws Exception {
        long bookCount = books.count(), authorCount = authors.count(), categoryCount = categories.count();
        when(provider.findByIsbn(ISBN)).thenReturn(Mono.just(new ExternalBookDto(
                ISBN, "Libro de prueba simulado", List.of("Autor de prueba"),
                "Editorial", "Descripción", null, List.of("Prueba"))));
        MvcResult pending = mvc.perform(get(PATH).with(user("test-reader").roles(role)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.title").value("Libro de prueba simulado"));
        assertEquals(bookCount, books.count());
        assertEquals(authorCount, authors.count());
        assertEquals(categoryCount, categories.count());
    }

    @Test
    void anonymousRequestNeverCallsGoogle() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
    }

    @Test
    void unauthorizedRoleNeverCallsGoogle() throws Exception {
        mvc.perform(get(PATH).with(user("other").roles("OTHER"))).andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }

    @Test
    void swaggerDocumentsProviderFailures() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/books/external/isbn/{isbn}'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/books/external/isbn/{isbn}'].get.responses['502']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/books/external/isbn/{isbn}'].get.responses['404']").exists());
    }
}
