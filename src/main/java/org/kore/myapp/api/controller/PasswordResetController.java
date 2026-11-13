package org.kore.myapp.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.doc.ApiDocs;
import org.kore.myapp.api.doc.FieldErrorsProblemDoc;
import org.kore.myapp.api.doc.ProblemDoc;
import org.kore.myapp.api.request.ConfirmPasswordResetRequest;
import org.kore.myapp.api.request.RequestPasswordResetRequest;
import org.kore.myapp.api.response.PasswordResetRequestedResponse;
import org.kore.myapp.core.port.PasswordResetUseCase;
import org.kore.myapp.core.valueobject.RequestPasswordResetCommand;
import org.kore.myapp.core.valueobject.ResetPasswordCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password-reset")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, and password reset. Public.")
@SecurityRequirements
public class PasswordResetController {

    private final PasswordResetUseCase passwordResetUseCase;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "requestPasswordReset", summary = "Send a one-time password-reset code", description = """
            Sends a 6-digit code to the given email address or phone number if it belongs to an account. Give \
            exactly one of `email` and `phone`; it also selects the channel.

            The answer is the same `202` whether or not an account exists, a code was sent, or a limit applied, so \
            it never reveals which contacts are registered. A code is valid for 5 minutes and usable once; a new \
            request replaces the previous code. An account gets at most 1 code per 60 seconds and 5 per rolling hour.

            Real email and SMS delivery is not connected yet: by default nothing is sent, and in development \
            `APP_PASSWORD_RESET_LOG_CODES=true` writes the code to the application log.""")
    @ApiResponse(responseCode = "202", description = "Accepted. Identical for every well-formed request.")
    @ApiResponse(responseCode = "400",
            description = "The contact is malformed, or both or neither of `email` and `phone` were given.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON,
                    schema = @Schema(implementation = FieldErrorsProblemDoc.class),
                    examples = {@ExampleObject(name = "badPhone", value = ApiDocs.RESET_BAD_PHONE),
                            @ExampleObject(name = "eitherOr", value = ApiDocs.RESET_EITHER_OR),
                            @ExampleObject(name = "malformed", ref = ApiDocs.MALFORMED_REQUEST)}))
    public PasswordResetRequestedResponse request(@RequestBody RequestPasswordResetRequest request) {
        passwordResetUseCase.requestReset(new RequestPasswordResetCommand(request.email(), request.phone()));
        return PasswordResetRequestedResponse.generic();
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "confirmPasswordReset", summary = "Replace the password using a reset code",
            description = """
                    Replaces the password of the account identified by `email` or `phone` (either contact detail of \
                    the account works, whichever one received the code). On success the code is used up and every \
                    session of the account ends; no token is issued.

                    Checks run in this order: input rules, then the code, then whether the new password equals the \
                    username or one of the last 3 passwords (the current one included). A refused new password does \
                    not use up the code or count as a wrong attempt. After 5 wrong codes the current code is \
                    invalidated.""")
    @ApiResponse(responseCode = "204", description = "Password replaced, code used up, all sessions of the account ended.")
    @ApiResponse(responseCode = "400", description = """
            Either the input or new password is refused (`Invalid password reset`, with `errors`), or the code \
            cannot be used (`Invalid or expired code`, the single answer for a wrong, expired, used, replaced, or \
            exhausted code and for an unknown contact).""",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON,
                    schema = @Schema(oneOf = {FieldErrorsProblemDoc.class, ProblemDoc.class}),
                    examples = {@ExampleObject(name = "passwordReused", value = ApiDocs.RESET_PASSWORD_REUSED),
                            @ExampleObject(name = "badInput", value = ApiDocs.RESET_BAD_INPUT),
                            @ExampleObject(name = "invalidCode", value = ApiDocs.RESET_INVALID_CODE),
                            @ExampleObject(name = "malformed", ref = ApiDocs.MALFORMED_REQUEST)}))
    @ApiResponse(responseCode = "503",
            description = "Another reset of the same account was still running past the lock timeout. Nothing changed; retry.",
            headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying.",
                    schema = @Schema(type = "integer", example = "1")),
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class),
                    examples = @ExampleObject(name = "busy", value = ApiDocs.RESET_BUSY)))
    public void confirm(@RequestBody ConfirmPasswordResetRequest request) {
        passwordResetUseCase.resetPassword(new ResetPasswordCommand(
                request.email(), request.phone(), request.code(), request.newPassword()));
    }
}
