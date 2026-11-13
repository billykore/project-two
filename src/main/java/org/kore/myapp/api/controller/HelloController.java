package org.kore.myapp.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.doc.ApiDocs;
import org.kore.myapp.api.doc.ProblemDoc;
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
@Tag(name = "Sample", description = "Sample endpoint for checking a token.")
public class HelloController {

    private final HelloUseCase useCase;

    @GetMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "hello", summary = "Sample endpoint for checking a token")
    @ApiResponse(responseCode = "200", description = "Greeting for the user.")
    @ApiResponse(responseCode = "401", ref = ApiDocs.UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", description = "No user has that username.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class),
                    examples = @ExampleObject(name = "notFound", value = ApiDocs.USER_NOT_FOUND)))
    public HelloResponse helloWorld(@PathVariable String username) {
        String message = useCase.sayHello(username);
        return HelloResponse.from(message);
    }
}
