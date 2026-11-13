package org.kore.myapp.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.doc.ApiDocs;
import org.kore.myapp.api.doc.ConflictProblemDoc;
import org.kore.myapp.api.doc.FieldErrorsProblemDoc;
import org.kore.myapp.api.doc.ProblemDoc;
import org.kore.myapp.api.request.LoginRequest;
import org.kore.myapp.api.request.RegisterRequest;
import org.kore.myapp.api.response.LoginResponse;
import org.kore.myapp.api.response.RegisterResponse;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.port.RegistrationUseCase;
import org.kore.myapp.core.valueobject.RegisterUserCommand;
import org.kore.myapp.core.valueobject.Token;
import org.kore.myapp.core.port.AuthUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, and password reset. Public.")
@SecurityRequirements
public class AuthController {

    private final AuthUseCase authUseCase;

    private final RegistrationUseCase registrationUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "login", summary = "Log in and receive an access token",
            description = "Identifies the account by username only (not email or phone) and records a login session.")
    @ApiResponse(responseCode = "200", description = "Logged in.")
    @ApiResponse(responseCode = "400", description = "`username` or `password` is blank, or the body is malformed.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class),
                    examples = {@ExampleObject(name = "blank", value = ApiDocs.LOGIN_BLANK),
                            @ExampleObject(name = "malformed", ref = ApiDocs.MALFORMED_REQUEST)}))
    @ApiResponse(responseCode = "401", description = "Unknown username or wrong password (the same answer for both).",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class),
                    examples = @ExampleObject(name = "invalidCredentials", value = ApiDocs.INVALID_CREDENTIALS)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Token loginToken = authUseCase.login(request.username(), request.password());
        return LoginResponse.from(loginToken);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "register", summary = "Create a customer account", description = """
            Creates an ordinary customer account. Does not log in or return a token; call `POST /auth/login` next. \
            Every rule is checked together, so a `400` lists every invalid field. Unknown properties (such as \
            `role`) are ignored.""")
    @ApiResponse(responseCode = "201", description = "Account created. The body never contains the password.")
    @ApiResponse(responseCode = "400", description = "One or more fields break a rule, or the body is malformed.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON,
                    schema = @Schema(implementation = FieldErrorsProblemDoc.class),
                    examples = {@ExampleObject(name = "invalid", value = ApiDocs.INVALID_REGISTRATION),
                            @ExampleObject(name = "malformed", ref = ApiDocs.MALFORMED_REQUEST)}))
    @ApiResponse(responseCode = "409",
            description = "The username, email, or phone already belongs to an account. Nothing is created.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON,
                    schema = @Schema(implementation = ConflictProblemDoc.class),
                    examples = @ExampleObject(name = "conflict", value = ApiDocs.REGISTRATION_CONFLICT)))
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        User user = registrationUseCase.register(new RegisterUserCommand(
                request.username(), request.password(), request.name(), request.email(), request.phone()));
        return RegisterResponse.from(user);
    }
}
