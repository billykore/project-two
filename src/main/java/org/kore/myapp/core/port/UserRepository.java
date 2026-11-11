package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;

import java.util.Optional;

/**
 * Finds {@link User}s.
 */
public interface UserRepository {

    /**
     * @param username username of the user.
     * @return the user, or empty if there is none with that username.
     */
    Optional<User> findByUsername(String username);
}
