package com.EduardoMango.Biblioteca.feature.book;

import com.EduardoMango.Biblioteca.feature.book.repository.BookRepository;
import com.EduardoMango.Biblioteca.feature.category.Category;
import com.EduardoMango.Biblioteca.feature.category.CategoryRepository;
import com.EduardoMango.Biblioteca.feature.loan.domain.Loan;
import com.EduardoMango.Biblioteca.feature.loan.domain.LoanStatus;
import com.EduardoMango.Biblioteca.feature.loan.repository.LoanRepository;
import com.EduardoMango.Biblioteca.feature.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:book-status-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost",
        "spring.mail.port=1",
        "app.scheduler.loan-reminder.cron=-"
})
@Transactional
class BookStatusControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private BookRepository bookRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private UserRepository userRepository;

    private MockMvc mockMvc;
    private Book book;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Category category = categoryRepository.save(Category.builder()
                .nombre("Category " + UUID.randomUUID()).descripcion("Test").build());
        book = bookRepository.saveAndFlush(Book.builder()
                .isbn(UUID.randomUUID().toString()).titulo("Test book")
                .stockTotal(2).stockDisponible(2).categoria(category).build());
    }

    @Test
    void anonymousUserCannotChangeStatus() throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SOCIO")
    void memberCannotChangeStatusOrListInactiveBooks() throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/libros/inactivos"))
                .andExpect(status().isForbidden());
        assertThat(book.isActivo()).isTrue();
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void librarianCanDeactivateAndReactivateWithoutChangingStock() throws Exception {
        changeStatus(false);
        changeStatus(false);
        assertThat(book.isActivo()).isFalse();
        assertThat(book.getStockDisponible()).isEqualTo(2);
        assertThat(book.getStockTotal()).isEqualTo(2);
        assertThat(bookRepository.findByIsbn(book.getIsbn())).isPresent();

        mockMvc.perform(get("/api/libros").param("isbn", book.getIsbn()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/libros/inactivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].activo").value(false));

        changeStatus(true);
        changeStatus(true);
        assertThat(book.getStockDisponible()).isEqualTo(2);
        mockMvc.perform(get("/api/libros").param("isbn", book.getIsbn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].activo").value(true));
    }

    @ParameterizedTest
    @EnumSource(value = LoanStatus.class, names = {"PRESTADO", "CON_RETRASO"})
    @WithMockUser(roles = "BIBLIOTECARIO")
    void pendingLoanPreventsDeactivation(LoanStatus loanStatus) throws Exception {
        saveLoan(loanStatus, null);
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(book.isActivo()).isTrue();
        assertThat(loanRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = LoanStatus.class, names = {"DEVUELTO", "CON_RETRASO"})
    @WithMockUser(roles = "BIBLIOTECARIO")
    void returnedLoanDoesNotPreventDeactivationAndHistoryIsPreserved(LoanStatus loanStatus) throws Exception {
        Loan loan = saveLoan(loanStatus, LocalDate.now());
        changeStatus(false);
        assertThat(loanRepository.findByPublicId(loan.getPublicId())).isPresent();
        assertThat(book.getStockTotal()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "socio_juan", roles = "SOCIO")
    void memberCannotBorrowOrOpenInactiveBook() throws Exception {
        book.setActivo(false);
        bookRepository.flush();
        mockMvc.perform(get("/api/libros/" + book.getIsbn()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/prestamos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libroIsbn\":\"" + book.getIsbn() + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(loanRepository.count()).isZero();
        assertThat(book.getStockDisponible()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void librarianCanOpenInactiveBook() throws Exception {
        book.setActivo(false);
        mockMvc.perform(get("/api/libros/" + book.getIsbn()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void missingStatusIsRejected() throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(book.isActivo()).isTrue();
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void missingBookReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/libros/missing/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void englishRouteSupportsStatusChanges() throws Exception {
        mockMvc.perform(patch("/api/v1/books/" + book.getIsbn() + "/estado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
    }

    private String statusUrl() {
        return "/api/libros/" + book.getIsbn() + "/estado";
    }

    private void changeStatus(boolean active) throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":" + active + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(active));
    }

    private Loan saveLoan(LoanStatus loanStatus, LocalDate returnedAt) {
        return loanRepository.saveAndFlush(Loan.builder().libro(book)
                .usuario(userRepository.findByEmail("juan.perez@email.com").orElseThrow())
                .estado(loanStatus).fechaPrestamo(LocalDate.now().minusDays(20))
                .fechaDevolucionEsperada(LocalDate.now().minusDays(6))
                .fechaDevolucionEfectiva(returnedAt).build());
    }
}
