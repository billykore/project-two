package org.kore.myapp.api.controller;

import lombok.RequiredArgsConstructor;
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
public class PasswordResetController {

    private final PasswordResetUseCase passwordResetUseCase;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetRequestedResponse request(@RequestBody RequestPasswordResetRequest request) {
        passwordResetUseCase.requestReset(new RequestPasswordResetCommand(request.email(), request.phone()));
        return PasswordResetRequestedResponse.generic();
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody ConfirmPasswordResetRequest request) {
        passwordResetUseCase.resetPassword(new ResetPasswordCommand(
                request.email(), request.phone(), request.code(), request.newPassword()));
    }
}
