package org.kore.myapp.api.doc;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Shape of an RFC 9457 Problem Details body, for the OpenAPI document only. The application writes these bodies with
 * Spring's {@code ProblemDetail}; this record just describes them.
 */
@Schema(name = "Problem", description = "RFC 9457 Problem Details.")
public record ProblemDoc(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "about:blank") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance
) {
}
