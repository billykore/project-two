package org.kore.myapp.core.exception;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.valueobject.FieldError;
import org.kore.myapp.core.valueobject.RegistrationField;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationExceptionsTest {

    @Test
    void invalidRegistrationMessageJoinsEachFieldAndReason() {
        List<FieldError> errors = new ArrayList<>(List.of(
                new FieldError(RegistrationField.PASSWORD, "must be at least 8 characters"),
                new FieldError(RegistrationField.EMAIL, "must be a valid email address")));

        InvalidRegistrationException ex = new InvalidRegistrationException(errors);

        assertThat(ex.getMessage())
                .isEqualTo("password: must be at least 8 characters; email: must be a valid email address");
        assertThat(ex.getErrors()).containsExactlyElementsOf(errors);
    }

    @Test
    void invalidRegistrationErrorsAreUnmodifiableAndDetachedFromTheInput() {
        List<FieldError> errors = new ArrayList<>(List.of(new FieldError(RegistrationField.NAME, "is required")));
        InvalidRegistrationException ex = new InvalidRegistrationException(errors);

        errors.clear();

        assertThat(ex.getErrors()).hasSize(1);
        assertThatThrownBy(() -> ex.getErrors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conflictMessageNamesEachFieldInUsernameEmailPhoneOrder() {
        RegistrationConflictException ex = new RegistrationConflictException(
                EnumSet.of(RegistrationField.PHONE, RegistrationField.USERNAME, RegistrationField.EMAIL));

        assertThat(ex.getMessage())
                .isEqualTo("username already taken; email already registered; phone number already registered");
        assertThat(ex.getFields()).containsExactlyInAnyOrder(
                RegistrationField.USERNAME, RegistrationField.EMAIL, RegistrationField.PHONE);
    }

    @Test
    void conflictMessageForASingleField() {
        assertThat(new RegistrationConflictException(Set.of(RegistrationField.EMAIL)).getMessage())
                .isEqualTo("email already registered");
        assertThat(new RegistrationConflictException(Set.of(RegistrationField.PHONE)).getMessage())
                .isEqualTo("phone number already registered");
    }

    @Test
    void conflictFieldsAreUnmodifiable() {
        RegistrationConflictException ex = new RegistrationConflictException(Set.of(RegistrationField.USERNAME));

        assertThatThrownBy(() -> ex.getFields().add(RegistrationField.EMAIL))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conflictRejectsAnEmptySet() {
        assertThatThrownBy(() -> new RegistrationConflictException(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conflictRejectsAFieldThatCannotConflict() {
        assertThatThrownBy(() -> new RegistrationConflictException(Set.of(RegistrationField.PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
