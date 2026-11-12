package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;

import java.util.Optional;

/**
 * Finds and stores {@link User}s.
 */
public interface UserRepository {

    /**
     * @param username username of the user.
     * @return the user, or empty if there is none with that username.
     */
    Optional<User> findByUsername(String username);

    /**
     * @param username username to look for.
     * @return true if an account has exactly this username.
     */
    boolean existsByUsername(String username);

    /**
     * @param email lowercase email address to look for.
     * @return true if an account has this email address.
     */
    boolean existsByEmail(String email);

    /**
     * @param phone phone number in international form to look for.
     * @return true if an account has this phone number.
     */
    boolean existsByPhone(String phone);

    /**
     * Inserts a new user and checks database constraints immediately.
     *
     * @param user the user to store.
     * @return the saved user, with its id.
     * @throws RegistrationConflictException if a unique constraint on username, email, or phone rejects the insert
     *                                       (for example a concurrent registration); names the field concerned.
     */
    User add(User user);
}
