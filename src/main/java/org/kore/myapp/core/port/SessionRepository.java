package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Session;

/**
 * Stores login {@link Session}s.
 */
public interface SessionRepository {

    /**
     * Stores a new session.
     *
     * @param session a session that has not been stored before.
     * @return the stored session.
     */
    Session add(Session session);
}
