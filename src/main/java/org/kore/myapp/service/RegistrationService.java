package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.PasswordHasher;
import org.kore.myapp.core.port.RegistrationUseCase;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.RegisterUserCommand;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService implements RegistrationUseCase {

    private final UserRepository userRepository;

    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        Set<RegistrationField> taken = takenFields(command);
        if (!taken.isEmpty()) {
            log.info("registration conflict, fields={}", taken);
            throw new RegistrationConflictException(taken);
        }
        User saved = userRepository.add(User.register(command, passwordHasher.hash(command.password())));
        log.info("user registered, id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    /**
     * Checks first so that a normal conflict names every taken field; the database constraints behind
     * {@link UserRepository#add} still decide when two registrations race.
     */
    private Set<RegistrationField> takenFields(RegisterUserCommand command) {
        Set<RegistrationField> taken = EnumSet.noneOf(RegistrationField.class);
        if (userRepository.existsByUsername(command.username())) {
            taken.add(RegistrationField.USERNAME);
        }
        if (userRepository.existsByEmail(command.email())) {
            taken.add(RegistrationField.EMAIL);
        }
        if (userRepository.existsByPhone(command.phone())) {
            taken.add(RegistrationField.PHONE);
        }
        return taken;
    }
}
