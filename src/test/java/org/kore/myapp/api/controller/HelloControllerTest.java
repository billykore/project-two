package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.exception.UserNotFoundException;
import org.kore.myapp.core.port.HelloUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HelloController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class})
class HelloControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HelloUseCase helloUseCase;

    @Test
    void greetsTheUser() throws Exception {
        when(helloUseCase.sayHello("budi")).thenReturn("Hello Budi");

        mvc.perform(get("/hello/{username}", "budi").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello Budi"));
    }

    @Test
    void missingTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/hello/{username}", "budi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        when(helloUseCase.sayHello("nobody")).thenThrow(new UserNotFoundException("nobody"));

        mvc.perform(get("/hello/{username}", "nobody").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User with username nobody not found"));
    }
}
