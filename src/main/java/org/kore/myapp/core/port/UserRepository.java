package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link User} related persistence operations.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Find a user by its username.
     *
     * @param username username of the user.
     * @return a {@link User}.
     */
    Optional<User> findByUsername(String username);
}
