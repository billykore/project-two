package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.InvalidCredentialsException;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.AuthUseCase;
import org.kore.myapp.core.port.RegistrationUseCase;
import org.kore.myapp.core.valueobject.RegisterUserCommand;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.kore.myapp.core.valueobject.Token;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class})
class AuthControllerTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthUseCase authUseCase;

    @MockitoBean
    private RegistrationUseCase registrationUseCase;

    @Test
    void loginReturnsTheTokenWithoutAuthentication() throws Exception {
        when(authUseCase.login("budi", "secret123")).thenReturn(
                new Token(SESSION_ID, "jwt-value", Instant.parse("2026-11-11T08:00:10Z")));

        mvc.perform(login("{\"username\": \"budi\", \"password\": \"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-11T08:00:10Z"));
    }

    @Test
    void blankCredentialsAreABadRequest() throws Exception {
        mvc.perform(login("{\"username\": \"\", \"password\": \"secret123\"}"))
                .andExpect(status().isBadRequest());
        verify(authUseCase, never()).login(anyString(), anyString());
    }

    @Test
    void invalidCredentialsAreUnauthorized() throws Exception {
        when(authUseCase.login("budi", "wrong")).thenThrow(new InvalidCredentialsException());

        mvc.perform(login("{\"username\": \"budi\", \"password\": \"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void registerCreatesTheAccountWithoutAuthenticationAndNeverReturnsAPassword() throws Exception {
        when(registrationUseCase.register(any(RegisterUserCommand.class))).thenReturn(User.builder()
                .id(42L).username("budi").name("Budi Santoso")
                .email("budi@example.com").phone("+6281234567890").password("bcrypt-hash").build());

        mvc.perform(register("""
                        {"username": "budi", "password": "secret123", "name": "  Budi Santoso ",
                         "email": "Budi@Example.com", "phone": "+6281234567890"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("budi"))
                .andExpect(jsonPath("$.name").value("Budi Santoso"))
                .andExpect(jsonPath("$.email").value("budi@example.com"))
                .andExpect(jsonPath("$.phone").value("+6281234567890"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registrationUseCase).register(command.capture());
        assertThat(command.getValue().name()).isEqualTo("Budi Santoso");
        assertThat(command.getValue().email()).isEqualTo("budi@example.com");
    }

    @Test
    void registerAndLoginIgnoreAnInvalidOrExpiredTokenSentAnyway() throws Exception {
        when(registrationUseCase.register(any(RegisterUserCommand.class))).thenReturn(User.builder()
                .id(42L).username("budi").name("Budi").email("budi@example.com").phone("+6281234567890")
                .password("bcrypt-hash").build());
        when(authUseCase.login("budi", "secret123")).thenReturn(
                new Token(SESSION_ID, "jwt-value", Instant.parse("2026-11-11T08:00:10Z")));

        mvc.perform(register("""
                        {"username": "budi", "password": "secret123", "name": "Budi",
                         "email": "budi@example.com", "phone": "+6281234567890"}""")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isCreated());
        mvc.perform(login("{\"username\": \"budi\", \"password\": \"secret123\"}")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void registerIgnoresUnknownPropertiesSuchAsARole() throws Exception {
        when(registrationUseCase.register(any(RegisterUserCommand.class))).thenReturn(User.builder()
                .id(7L).username("ani").name("Ani").email("ani@example.com").phone("+6281200000000").build());

        mvc.perform(register("""
                        {"username": "ani", "password": "secret123", "name": "Ani",
                         "email": "ani@example.com", "phone": "+6281200000000", "role": "DRIVER"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void registerReportsEveryTakenFieldAsAConflict() throws Exception {
        when(registrationUseCase.register(any(RegisterUserCommand.class))).thenThrow(
                new RegistrationConflictException(EnumSet.of(RegistrationField.USERNAME, RegistrationField.EMAIL)));

        mvc.perform(register("""
                        {"username": "budi", "password": "secret123", "name": "Budi",
                         "email": "budi@example.com", "phone": "+6281234567890"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Registration conflict"))
                .andExpect(jsonPath("$.detail").value("username already taken; email already registered"))
                .andExpect(jsonPath("$.conflicts[0]").value("username"))
                .andExpect(jsonPath("$.conflicts[1]").value("email"))
                .andExpect(jsonPath("$.conflicts.length()").value(2));
    }

    @Test
    void registerListsEveryInvalidFieldAndNeverCallsTheUseCase() throws Exception {
        mvc.perform(register("""
                        {"username": "budi", "password": "short12", "name": "Budi",
                         "email": "budi@", "phone": "081234567890"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid registration"))
                .andExpect(jsonPath("$.detail").value("password: must be at least 8 characters; "
                        + "email: must be a valid email address; "
                        + "phone: must be in international format, e.g. +6281234567890"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at least 8 characters"))
                .andExpect(jsonPath("$.errors[1].field").value("email"))
                .andExpect(jsonPath("$.errors[2].field").value("phone"));
        verify(registrationUseCase, never()).register(any());
    }

    @Test
    void registerReportsAMissingPropertyAsRequired() throws Exception {
        mvc.perform(register("""
                        {"username": "budi", "password": "secret123", "email": "budi@example.com",
                         "phone": "+6281234567890"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
        verify(registrationUseCase, never()).register(any());
    }

    @Test
    void registerRejectsAnEmptyBodyAsMalformed() throws Exception {
        mvc.perform(register(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON"));
        verify(registrationUseCase, never()).register(any());
    }

    @Test
    void registerRejectsInvalidJsonAsMalformedWithoutEchoingTheBody() throws Exception {
        mvc.perform(register("{\"username\": \"budi\", \"password\": \"secret-in-body\" oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-in-body"))));
        verify(registrationUseCase, never()).register(any());
    }

    private static MockHttpServletRequestBuilder register(String body) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
