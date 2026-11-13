package org.kore.myapp.api.doc;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A {@link ProblemDoc} that lists every invalid field, for the OpenAPI document only.
 */
@Schema(name = "FieldErrorsProblem", description = "Problem Details listing every invalid field.")
public record FieldErrorsProblemDoc(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "about:blank") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every invalid field, once each, in field order."))
        List<FieldErrorDoc> errors
) {

    @Schema(name = "FieldError")
    public record FieldErrorDoc(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Property name as the client sends it.") String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message
    ) {
    }
}
