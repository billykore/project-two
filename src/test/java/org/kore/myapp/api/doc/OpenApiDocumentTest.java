package org.kore.myapp.api.doc;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.OpenApiConfig;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.port.AuthUseCase;
import org.kore.myapp.core.port.HelloUseCase;
import org.kore.myapp.core.port.PasswordResetUseCase;
import org.kore.myapp.core.port.RegistrationUseCase;
import org.kore.myapp.core.port.TripOfferUseCase;
import org.kore.myapp.core.port.TripUseCase;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps {@code docs/openapi.yaml} identical to the document springdoc generates from the controllers, so a change
 * to the HTTP contract shows up in review (constitution Principle III). Runs against every controller with the use
 * cases mocked; no database or broker is needed.
 * <p>
 * When the contract changes on purpose, this test fails and writes the new document to {@code target/openapi.yaml};
 * copy it over {@code docs/openapi.yaml}.
 */
@WebMvcTest
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class, OpenApiConfig.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocSecurityConfiguration.class, SpringDocSpecPropertiesConfiguration.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiDocumentTest {

    private static final Path CHECKED_IN = Path.of("docs/openapi.yaml");
    private static final Path GENERATED = Path.of("target/openapi.yaml");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthUseCase authUseCase;

    @MockitoBean
    private RegistrationUseCase registrationUseCase;

    @MockitoBean
    private PasswordResetUseCase passwordResetUseCase;

    @MockitoBean
    private TripUseCase tripUseCase;

    @MockitoBean
    private TripOfferUseCase tripOfferUseCase;

    @MockitoBean
    private HelloUseCase helloUseCase;

    @Test
    void theCheckedInDocumentMatchesTheGeneratedOne() throws Exception {
        String generated = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String checkedIn = Files.exists(CHECKED_IN) ? Files.readString(CHECKED_IN) : "";

        if (!generated.equals(checkedIn)) {
            write(generated);
        }
        assertThat(checkedIn)
                .as("docs/openapi.yaml is out of date; the generated document was written to %s, copy it over", GENERATED)
                .isEqualTo(generated);
    }

    @Test
    void theDocumentationIsPublicAndIgnoresATokenSentAnyway() throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("swagger-ui/index.html")));
    }

    private static void write(String document) throws IOException {
        Files.createDirectories(GENERATED.getParent());
        Files.writeString(GENERATED, document);
    }
}
