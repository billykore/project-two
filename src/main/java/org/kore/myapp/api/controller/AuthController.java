package org.kore.myapp.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.request.LoginRequest;
import org.kore.myapp.api.response.LoginResponse;
import org.kore.myapp.core.valueobject.Token;
import org.kore.myapp.core.port.AuthUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Token loginToken = authUseCase.login(request.username(), request.password());
        return LoginResponse.from(loginToken);
    }
}
