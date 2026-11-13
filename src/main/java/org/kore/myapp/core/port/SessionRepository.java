package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Session;

import java.time.Instant;

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

    /**
     * Ends every session of the user that has not ended yet.
     *
     * @param userId the account.
     * @param at     the revocation time to record.
     * @return the number of sessions ended.
     */
    int revokeAllForUser(long userId, Instant at);
}
