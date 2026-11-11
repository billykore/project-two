package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.port.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link UserRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository users;

    JpaUserRepository(SpringDataUserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(username);
    }
}
