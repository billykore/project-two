package org.kore.myapp.api.doc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A {@link ProblemDoc} for a customer who still has an unfinished trip, for the OpenAPI document only.
 */
@Schema(name = "ActiveTripProblem", description = "Problem Details for a customer who still has an unfinished trip.")
public record ActiveTripProblemDoc(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "about:blank") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance,
        @Schema(description = "The unfinished trip; absent when it was created by a concurrent request.")
        UUID activeTripId
) {
}
