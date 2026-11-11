package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.exception.InvalidCredentialsException;
import org.kore.myapp.core.port.AuthUseCase;
import org.kore.myapp.core.valueobject.Token;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

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

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
