package org.kore.myapp.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AuthController {

    private final AuthUseCase authUseCase;

    private final RegistrationUseCase registrationUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Token loginToken = authUseCase.login(request.username(), request.password());
        return LoginResponse.from(loginToken);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        User user = registrationUseCase.register(new RegisterUserCommand(
                request.username(), request.password(), request.name(), request.email(), request.phone()));
        return RegisterResponse.from(user);
    }
}
