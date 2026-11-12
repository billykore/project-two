package org.kore.myapp.core.exception;

import org.kore.myapp.core.valueobject.RegistrationField;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A registration reuses a username, email address, or phone number that already belongs to an account.
 */
public class RegistrationConflictException extends RuntimeException {

    private final Set<RegistrationField> fields;

    /**
     * @param fields the fields already in use, never empty; only username, email and phone can conflict.
     * @throws IllegalArgumentException if {@code fields} is empty or holds a field that cannot conflict.
     */
    public RegistrationConflictException(Set<RegistrationField> fields) {
        super(describe(fields));
        this.fields = Collections.unmodifiableSet(EnumSet.copyOf(fields));
    }

    /**
     * @return the conflicting fields in username, email, phone order, unmodifiable.
     */
    public Set<RegistrationField> getFields() {
        return fields;
    }

    private static String describe(Set<RegistrationField> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("A conflict needs at least one field");
        }
        return EnumSet.copyOf(fields).stream()
                .map(RegistrationConflictException::describe)
                .collect(Collectors.joining("; "));
    }

    private static String describe(RegistrationField field) {
        return switch (field) {
            case USERNAME -> "username already taken";
            case EMAIL -> "email already registered";
            case PHONE -> "phone number already registered";
            default -> throw new IllegalArgumentException(field + " cannot conflict");
        };
    }
}
