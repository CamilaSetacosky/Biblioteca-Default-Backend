package com.EduardoMango.Biblioteca.feature.user.controller;

import com.EduardoMango.Biblioteca.feature.auth.domain.*;
import com.EduardoMango.Biblioteca.feature.auth.dto.AuthRequest;
import com.EduardoMango.Biblioteca.feature.auth.repository.*;
import com.EduardoMango.Biblioteca.feature.book.Book;
import com.EduardoMango.Biblioteca.feature.book.repository.BookRepository;
import com.EduardoMango.Biblioteca.feature.category.Category;
import com.EduardoMango.Biblioteca.feature.category.CategoryRepository;
import com.EduardoMango.Biblioteca.feature.loan.domain.*;
import com.EduardoMango.Biblioteca.feature.loan.repository.LoanRepository;
import com.EduardoMango.Biblioteca.feature.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-status-test;DB_CLOSE_DELAY=-1",
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
class UserDeactivationControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private CredentialsRepository credentials;
    @Autowired private RoleRepository roles;
    @Autowired private LoanRepository loans;
    @Autowired private BookRepository books;
    @Autowired private CategoryRepository categories;
    @Autowired private AccountVerificationTokenRepository verificationTokens;
    @Autowired private PasswordEncoder encoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;
    private UserEntity member;
    private CredentialsEntity memberCredentials;
    private String username;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        username = "member_" + UUID.randomUUID();
        member = users.saveAndFlush(UserEntity.builder().nombre("Test").apellido("Member")
                .email(username + "@example.test").rol("SOCIO").activo(true).enabled(true).build());
        memberCredentials = credentials.saveAndFlush(CredentialsEntity.builder()
                .username(username).password(encoder.encode("test-password"))
                .usuario(member).enabled(true)
                .roles(new HashSet<>(Set.of(roles.findByRole(Roles.ROLE_SOCIO).orElseThrow()))).build());
    }

    @Test
    void anonymousUserCannotChangeStatus() throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "socio_juan", roles = "SOCIO")
    void memberCannotChangeStatus() throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void deactivationPreservesAccountAndReactivationAllowsLogin() throws Exception {
        changeStatus(false);
        assertThat(users.findByPublicId(member.getPublicId())).isPresent();
        assertThat(memberCredentials.isEnabled()).isFalse();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthRequest(username, "test-password"))))
                .andExpect(status().isUnauthorized());

        changeStatus(true);
        assertThat(memberCredentials.isEnabled()).isTrue();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthRequest(username, "test-password"))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = LoanStatus.class, names = {"PRESTADO", "CON_RETRASO"})
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void unreturnedLoanBlocksDeactivation(LoanStatus status) throws Exception {
        createLoan(status, null);
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(member.getActivo()).isTrue();
        assertThat(memberCredentials.isEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void lateReturnedLoanDoesNotBlockDeactivationAndHistoryIsPreserved() throws Exception {
        Loan loan = createLoan(LoanStatus.CON_RETRASO, LocalDate.now());
        changeStatus(false);
        assertThat(loans.findByPublicId(loan.getPublicId())).isPresent();
        assertThat(member.getActivo()).isFalse();
    }

    @Test
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void ownAccountCannotBeDeactivated() throws Exception {
        UUID adminId = credentials.findByUsername("bibliotecario").orElseThrow().getUsuario().getPublicId();
        mockMvc.perform(patch("/api/usuarios/" + adminId + "/estado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void administrativeReactivationCannotConfirmAnUnverifiedAccount() throws Exception {
        member.setActivo(false);
        member.setEnabled(false);
        memberCredentials.setEnabled(false);
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isBadRequest());
        assertThat(member.getActivo()).isFalse();
    }

    @Test
    @WithMockUser(username = "bibliotecario", roles = "BIBLIOTECARIO")
    void disabledAccountCannotBeReactivatedWithAVerificationToken() throws Exception {
        String token = UUID.randomUUID().toString();
        verificationTokens.saveAndFlush(AccountVerificationToken.builder().token(token).user(member)
                .expiryDate(LocalDateTime.now().plusMinutes(10)).used(false).build());
        changeStatus(false);
        mockMvc.perform(post("/api/auth/confirm-account").param("token", token))
                .andExpect(status().isBadRequest());
        assertThat(member.getActivo()).isFalse();
        assertThat(memberCredentials.isEnabled()).isFalse();
    }

    @Test
    void previouslyIssuedAccessAndRefreshTokensStopWorkingAfterDeactivation() throws Exception {
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthRequest(username, "test-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String accessToken = mapper.readTree(loginBody).get("accessToken").asText();
        String refreshToken = mapper.readTree(loginBody).get("refreshToken").asText();
        String adminBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bibliotecario\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminToken = mapper.readTree(adminBody).get("accessToken").asText();

        mockMvc.perform(patch(statusUrl()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isBadRequest());
        assertThat(memberCredentials.getRefreshToken()).isNull();
    }

    private String statusUrl() {
        return "/api/usuarios/" + member.getPublicId() + "/estado";
    }

    private void changeStatus(boolean active) throws Exception {
        mockMvc.perform(patch(statusUrl()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":" + active + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(active));
    }

    private Loan createLoan(LoanStatus status, LocalDate returnedAt) {
        Category category = categories.save(Category.builder()
                .nombre(UUID.randomUUID().toString()).descripcion("Test").build());
        Book book = books.save(Book.builder().isbn(UUID.randomUUID().toString()).titulo("Test book")
                .categoria(category).stockTotal(1).stockDisponible(returnedAt == null ? 0 : 1).build());
        return loans.saveAndFlush(Loan.builder().libro(book).usuario(member).estado(status)
                .fechaPrestamo(LocalDate.now().minusDays(20))
                .fechaDevolucionEsperada(LocalDate.now().minusDays(6))
                .fechaDevolucionEfectiva(returnedAt).build());
    }
}
