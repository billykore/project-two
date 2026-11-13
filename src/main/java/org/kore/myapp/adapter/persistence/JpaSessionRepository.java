package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.Session;
import org.kore.myapp.core.port.SessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * {@link SessionRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaSessionRepository implements SessionRepository {

    private final SpringDataSessionRepository sessions;

    JpaSessionRepository(SpringDataSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public Session add(Session session) {
        return sessions.save(session);
    }

    @Override
    public int revokeAllForUser(long userId, Instant at) {
        return sessions.revokeAllForUser(userId, at);
    }
}
