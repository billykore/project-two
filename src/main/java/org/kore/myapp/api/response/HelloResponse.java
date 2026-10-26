package org.kore.myapp.api.response;

public record HelloResponse(String message) {
    public static HelloResponse from(String message) {
        return new HelloResponse(message);
    }
}
