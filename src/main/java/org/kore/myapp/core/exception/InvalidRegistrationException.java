package org.kore.myapp.core.exception;

import lombok.Getter;
import org.kore.myapp.core.valueobject.FieldError;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A registration breaks one or more input rules. Lists every invalid field, not only the first.
 */
@Getter
public class InvalidRegistrationException extends RuntimeException {

    /**
     * -- GETTER --
     *
     * @return the invalid fields, unmodifiable.
     */
    private final List<FieldError> errors;

    /**
     * @param errors the invalid fields in field order, never empty.
     */
    public InvalidRegistrationException(List<FieldError> errors) {
        super(errors.stream()
                .map(e -> e.field() + ": " + e.message())
                .collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }

}
