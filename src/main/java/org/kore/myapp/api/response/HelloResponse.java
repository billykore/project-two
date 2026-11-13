package org.kore.myapp.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

public record HelloResponse(@Schema(requiredMode = RequiredMode.REQUIRED, example = "Hello Budi") String message) {
    public static HelloResponse from(String message) {
        return new HelloResponse(message);
    }
}
