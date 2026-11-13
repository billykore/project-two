package org.kore.myapp.api.doc;

/**
 * Names and example bodies shared by the OpenAPI annotations of the HTTP adapter. Annotation values must be
 * compile-time constants, so the examples are JSON text.
 */
public final class ApiDocs {

    public static final String PROBLEM_JSON = "application/problem+json";

    /** Reusable response for a missing, invalid, or expired token, declared in {@code OpenApiConfig}. */
    public static final String UNAUTHENTICATED = "#/components/responses/Unauthenticated";

    /** Reusable example for a missing or non-JSON body, declared in {@code OpenApiConfig}. */
    public static final String MALFORMED_REQUEST = "#/components/examples/MalformedRequest";

    public static final String INVALID_REGISTRATION = """
            {"type": "about:blank", "title": "Invalid registration", "status": 400,
             "detail": "password: must be at least 8 characters; phone: must be in international format, e.g. +6281234567890",
             "instance": "/auth/register",
             "errors": [{"field": "password", "message": "must be at least 8 characters"},
                        {"field": "phone", "message": "must be in international format, e.g. +6281234567890"}]}""";

    public static final String REGISTRATION_CONFLICT = """
            {"type": "about:blank", "title": "Registration conflict", "status": 409,
             "detail": "username already taken; email already registered", "instance": "/auth/register",
             "conflicts": ["username", "email"]}""";

    public static final String LOGIN_BLANK = """
            {"type": "about:blank", "title": "Bad Request", "status": 400,
             "detail": "username: must not be blank", "instance": "/auth/login"}""";

    public static final String INVALID_CREDENTIALS = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Invalid credentials", "instance": "/auth/login"}""";

    public static final String RESET_BAD_PHONE = """
            {"type": "about:blank", "title": "Invalid password reset", "status": 400,
             "detail": "phone: must be in international format, e.g. +6281234567890",
             "instance": "/auth/password-reset/request",
             "errors": [{"field": "phone", "message": "must be in international format, e.g. +6281234567890"}]}""";

    public static final String RESET_EITHER_OR = """
            {"type": "about:blank", "title": "Invalid password reset", "status": 400,
             "detail": "email: provide either email or phone; phone: provide either email or phone",
             "instance": "/auth/password-reset/request",
             "errors": [{"field": "email", "message": "provide either email or phone"},
                        {"field": "phone", "message": "provide either email or phone"}]}""";

    public static final String RESET_PASSWORD_REUSED = """
            {"type": "about:blank", "title": "Invalid password reset", "status": 400,
             "detail": "newPassword: must not be one of your last 3 passwords",
             "instance": "/auth/password-reset/confirm",
             "errors": [{"field": "newPassword", "message": "must not be one of your last 3 passwords"}]}""";

    public static final String RESET_BAD_INPUT = """
            {"type": "about:blank", "title": "Invalid password reset", "status": 400,
             "detail": "code: must be 6 digits; newPassword: must be at least 8 characters",
             "instance": "/auth/password-reset/confirm",
             "errors": [{"field": "code", "message": "must be 6 digits"},
                        {"field": "newPassword", "message": "must be at least 8 characters"}]}""";

    public static final String RESET_INVALID_CODE = """
            {"type": "about:blank", "title": "Invalid or expired code", "status": 400,
             "detail": "Invalid or expired code", "instance": "/auth/password-reset/confirm"}""";

    public static final String RESET_BUSY = """
            {"type": "about:blank", "title": "Password reset busy", "status": 503,
             "detail": "Another password reset for this account is in progress; try again",
             "instance": "/auth/password-reset/confirm"}""";

    public static final String INVALID_TRIP = """
            {"type": "about:blank", "title": "Invalid trip", "status": 400,
             "detail": "Unknown service type, expected MyRide or MyCar", "instance": "/trips"}""";

    public static final String ACTIVE_TRIP = """
            {"type": "about:blank", "title": "Active trip exists", "status": 409,
             "detail": "You already have an active trip", "instance": "/trips",
             "activeTripId": "3f8a6c2e-9d41-4f7b-8a3e-0c1d2e3f4a5b"}""";

    public static final String USER_NOT_FOUND = """
            {"type": "about:blank", "title": "Not Found", "status": 404,
             "detail": "User with username nobody not found", "instance": "/hello/nobody"}""";

    private ApiDocs() {
    }
}
