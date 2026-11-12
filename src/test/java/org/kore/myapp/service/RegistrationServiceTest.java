package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.PasswordHasher;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.RegisterUserCommand;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private UserRepository users;
    private PasswordHasher passwordHasher;
    private RegistrationService service;
    private final RegisterUserCommand command = new RegisterUserCommand(
            "budi", "secret123", "Budi Santoso", "budi@example.com", "+6281234567890");

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        service = new RegistrationService(users, passwordHasher);
        when(passwordHasher.hash("secret123")).thenReturn("bcrypt-hash");
        when(users.add(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void registerSavesAUserHoldingTheHashAndReturnsIt() {
        User saved = service.register(command);

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(users).add(stored.capture());
        assertThat(stored.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(stored.getValue().getUsername()).isEqualTo("budi");
        assertThat(stored.getValue().getEmail()).isEqualTo("budi@example.com");
        assertThat(stored.getValue().getPhone()).isEqualTo("+6281234567890");
        assertThat(saved).isSameAs(stored.getValue());
    }

    @Test
    void registerHashesThePasswordOnceAndNeverPassesItToTheRepository() {
        service.register(command);

        verify(passwordHasher).hash("secret123");
        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(users).add(stored.capture());
        assertThat(stored.getValue().getPassword()).doesNotContain("secret123");
    }

    @Test
    void registerReportsATakenUsername() {
        when(users.existsByUsername("budi")).thenReturn(true);

        assertConflict(RegistrationField.USERNAME);
    }

    @Test
    void registerReportsATakenEmail() {
        when(users.existsByEmail("budi@example.com")).thenReturn(true);

        assertConflict(RegistrationField.EMAIL);
    }

    @Test
    void registerReportsATakenPhone() {
        when(users.existsByPhone("+6281234567890")).thenReturn(true);

        assertConflict(RegistrationField.PHONE);
    }

    @Test
    void registerNamesEveryTakenField() {
        when(users.existsByUsername("budi")).thenReturn(true);
        when(users.existsByEmail("budi@example.com")).thenReturn(true);
        when(users.existsByPhone("+6281234567890")).thenReturn(true);

        assertConflict(RegistrationField.USERNAME, RegistrationField.EMAIL, RegistrationField.PHONE);
    }

    private void assertConflict(RegistrationField... expected) {
        assertThatThrownBy(() -> service.register(command))
                .isInstanceOfSatisfying(RegistrationConflictException.class,
                        e -> assertThat(e.getFields()).containsExactlyInAnyOrder(expected));
        verify(passwordHasher, never()).hash(any());
        verify(users, never()).add(any());
    }
}
