package org.kore.myapp.api.controller;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.response.HelloResponse;
import org.kore.myapp.core.port.HelloUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
@RequiredArgsConstructor
public class HelloController {
    private final HelloUseCase useCase;

    @GetMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    public HelloResponse helloWorld(@PathVariable String username) {
        String message = useCase.sayHello(username);
        return HelloResponse.from(message);
    }
}
