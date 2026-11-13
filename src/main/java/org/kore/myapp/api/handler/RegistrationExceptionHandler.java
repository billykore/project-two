package org.kore.myapp.api.handler;

import org.kore.myapp.core.exception.InvalidRegistrationException;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class RegistrationExceptionHandler {

    @ExceptionHandler(RegistrationConflictException.class)
    public ProblemDetail handleConflict(RegistrationConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Registration conflict");
        List<String> fields = ex.getFields().stream().map(RegistrationField::jsonName).toList();
        pd.setProperty("conflicts", fields);
        return pd;
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    public ProblemDetail handleInvalid(InvalidRegistrationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid registration");
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(e -> Map.of("field", e.field(), "message", e.message()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }
}
