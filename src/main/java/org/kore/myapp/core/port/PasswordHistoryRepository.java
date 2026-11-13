package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.PasswordHistoryEntry;

import java.util.List;

/**
 * Finds and stores the passwords an account used to have.
 */
public interface PasswordHistoryRepository {

    /**
     * @param entry an entry that has not been stored before.
     * @return the stored entry, with its id.
     */
    PasswordHistoryEntry add(PasswordHistoryEntry entry);

    /**
     * @param userId the account.
     * @param limit  the most hashes to return.
     * @return up to {@code limit} password hashes of the account, newest first.
     */
    List<String> findRecentHashes(long userId, int limit);

    /**
     * Deletes all but the newest entries of an account.
     *
     * @param userId the account.
     * @param keep   how many of the newest entries to keep.
     */
    void keepNewest(long userId, int keep);
}
