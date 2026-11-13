package org.kore.myapp.api.doc;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A {@link ProblemDoc} naming the registration fields already in use, for the OpenAPI document only.
 */
@Schema(name = "ConflictProblem", description = "Problem Details naming the fields already in use.")
public record ConflictProblemDoc(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "about:blank") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance,
        @ArraySchema(
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = """
                        Fields already in use, ordered username, email, phone. In a simultaneous-registration race it \
                        may name only one field."""),
                schema = @Schema(allowableValues = {"username", "email", "phone"}))
        List<String> conflicts
) {
}
