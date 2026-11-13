package org.kore.myapp.api.doc;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.controller.HelloController;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.OpenApiConfig;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.port.HelloUseCase;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without {@code APP_API_DOCS_ENABLED=true} the API documentation is not served, so a production deployment does not
 * expose it by accident.
 */
@WebMvcTest(HelloController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class, OpenApiConfig.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
class ApiDocsDisabledByDefaultTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HelloUseCase helloUseCase;

    @Test
    void theOpenApiDocumentIsNotServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotServed() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
