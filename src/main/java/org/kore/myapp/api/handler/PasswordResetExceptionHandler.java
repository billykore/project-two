package org.kore.myapp.api.handler;

import org.kore.myapp.core.exception.InvalidPasswordResetException;
import org.kore.myapp.core.exception.InvalidResetCodeException;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class PasswordResetExceptionHandler {

    @ExceptionHandler(InvalidPasswordResetException.class)
    public ProblemDetail handleInvalid(InvalidPasswordResetException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid password reset");
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(e -> Map.of("field", e.field(), "message", e.message()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(InvalidResetCodeException.class)
    public ProblemDetail handleInvalidCode(InvalidResetCodeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid or expired code");
        return pd;
    }

    @ExceptionHandler(PasswordResetBusyException.class)
    public ResponseEntity<ProblemDetail> handleBusy(PasswordResetBusyException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Password reset busy");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(pd);
    }
}
