package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.valueobject.RegisterUserCommand;

/**
 * Business logic for creating accounts.
 */
public interface RegistrationUseCase {

    /**
     * Creates a customer account.
     *
     * @param command validated, normalized registration input.
     * @return the saved user, with its id.
     * @throws RegistrationConflictException if the username, email, or phone already belongs to an account; names
     *                                       every such field or, in a concurrent race, at least one of them.
     */
    User register(RegisterUserCommand command);
}
