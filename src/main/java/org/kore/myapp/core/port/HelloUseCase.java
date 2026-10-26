package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;

/**
 * Core business logic for greeting service.
 */
public interface HelloUseCase {
    /**
     * Say hello to a user.
     *
     * @param username username of a {@link User}.
     * @return greetings to user.
     */
    String sayHello(String username);
}
