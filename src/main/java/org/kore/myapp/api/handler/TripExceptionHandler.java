package org.kore.myapp.api.handler;

import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TripExceptionHandler {

    @ExceptionHandler(ActiveTripExistsException.class)
    public ProblemDetail handleActiveTripExists(ActiveTripExistsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "You already have an active trip"
        );
        pd.setTitle("Active trip exists");
        pd.setProperty("activeTripId", ex.getActiveTripId());
        return pd;
    }

    @ExceptionHandler(InvalidTripException.class)
    public ProblemDetail handleInvalidTrip(InvalidTripException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid trip");
        return pd;
    }
}
