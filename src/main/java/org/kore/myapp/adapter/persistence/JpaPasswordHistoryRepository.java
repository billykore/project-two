package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.PasswordHistoryEntry;
import org.kore.myapp.core.port.PasswordHistoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link PasswordHistoryRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaPasswordHistoryRepository implements PasswordHistoryRepository {

    private final SpringDataPasswordHistoryRepository history;

    JpaPasswordHistoryRepository(SpringDataPasswordHistoryRepository history) {
        this.history = history;
    }

    /**
     * Saves and flushes, so the entry exists in the database before {@link #keepNewest} counts the account's
     * entries. An integrity violation is a bug and is reported as {@link IllegalStateException} naming the
     * constraint.
     */
    @Override
    public PasswordHistoryEntry add(PasswordHistoryEntry entry) {
        try {
            return history.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw IntegrityViolations.unexpected("Password history entry", e);
        }
    }

    @Override
    public List<String> findRecentHashes(long userId, int limit) {
        return history.findRecentHashes(userId, PageRequest.of(0, limit));
    }

    @Override
    public void keepNewest(long userId, int keep) {
        history.keepNewest(userId, keep);
    }
}
