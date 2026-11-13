package org.kore.myapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parts of the OpenAPI document that belong to the whole API rather than one operation: title, server, the
 * Bearer scheme, and the reusable 401 response and malformed-body example. Operations are described by the
 * annotations on the controllers. Served only when {@code APP_API_DOCS_ENABLED=true}; the checked-in copy is
 * {@code docs/openapi.yaml}.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    private static final String DESCRIPTION = """
            HTTP API of the myapp ride-hailing backend.

            **Authentication.** Every endpoint except `/auth/**` requires `Authorization: Bearer <accessToken>`, \
            where the token comes from `POST /auth/login`. A token sent to `/auth/**` is ignored, even when it is \
            expired or invalid.

            **Errors.** All errors are [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) \
            (`application/problem+json`). A missing or non-JSON request body on any endpoint gets `400` titled \
            `Malformed request`.""";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("myapp — Ride-Hailing Backend API").version("0.1.0").description(DESCRIPTION))
                .addServersItem(new Server().url("http://localhost:8080").description("Local development"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("HS256 JWT from `POST /auth/login`. Its `sub` is the numeric user id."))
                        .addResponses("Unauthenticated", unauthenticated())
                        .addExamples("MalformedRequest", new Example()
                                .summary("Missing or non-JSON body")
                                .value(problem(400, "Malformed request",
                                        "Request body is missing or is not valid JSON"))));
    }

    private static ApiResponse unauthenticated() {
        return new ApiResponse()
                .description("The token is missing, invalid, or expired, or it has no numeric user id in `sub`.")
                .addHeaderObject("WWW-Authenticate", new Header()
                        .schema(new Schema<String>().type("string").example("Bearer")))
                .content(new Content().addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/Problem"))
                        .example(problem(401, "Unauthenticated", "Token is missing, invalid, or expired"))));
    }

    private static Map<String, Object> problem(int status, String title, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        return body;
    }
}
