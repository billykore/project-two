package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.PasswordHistoryEntry;
import org.kore.myapp.core.port.PasswordHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JpaPasswordHistoryRepository} against PostgreSQL with the real Flyway migrations.
 */
@PersistenceAdapterTest
class JpaPasswordHistoryRepositoryIT {

    private static final Instant NOW = PersistenceFixtures.NOW;

    @Autowired
    private PasswordHistoryRepository history;

    @Autowired
    private PersistenceFixtures db;

    @Autowired
    private JdbcTemplate jdbc;

    private long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long digits = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        return db.insertUser("history", "history-" + suffix + "@example.com", "+62" + digits);
    }

    private void add(long userId, String hash, long secondsAfterNow) {
        history.add(PasswordHistoryEntry.of(userId, hash, NOW.plusSeconds(secondsAfterNow)));
    }

    @Test
    void addStoresTheEntryAndAssignsAnId() {
        long userId = newUser();

        PasswordHistoryEntry saved = history.add(PasswordHistoryEntry.of(userId, "hash-one", NOW));
        db.flushAndClear();

        PasswordHistoryEntry reloaded = db.find(PasswordHistoryEntry.class, saved.getId());
        assertThat(saved.getId()).isNotNull();
        assertThat(reloaded.getUserId()).isEqualTo(userId);
        assertThat(reloaded.getPasswordHash()).isEqualTo("hash-one");
        assertThat(reloaded.getReplacedAt()).isEqualTo(NOW);
    }

    @Test
    void findRecentHashesReturnsTheNewestFirstAndHonoursTheLimit() {
        long userId = newUser();
        add(userId, "oldest", 0);
        add(userId, "middle", 10);
        add(userId, "newest", 20);
        db.flushAndClear();

        assertThat(history.findRecentHashes(userId, 2)).containsExactly("newest", "middle");
        assertThat(history.findRecentHashes(userId, 5)).containsExactly("newest", "middle", "oldest");
        assertThat(history.findRecentHashes(userId, 1)).containsExactly("newest");
    }

    @Test
    void findRecentHashesBreaksTiesOnTheTimeByTheNewestId() {
        long userId = newUser();
        add(userId, "first-inserted", 5);
        add(userId, "second-inserted", 5);
        db.flushAndClear();

        assertThat(history.findRecentHashes(userId, 2)).containsExactly("second-inserted", "first-inserted");
    }

    @Test
    void findRecentHashesIgnoresOtherAccounts() {
        long mine = newUser();
        long theirs = newUser();
        add(theirs, "theirs", 0);
        db.flushAndClear();

        assertThat(history.findRecentHashes(mine, 2)).isEmpty();
    }

    @Test
    void keepNewestDeletesAllButTheNewestEntriesOfThatAccountOnly() {
        long userId = newUser();
        long otherUser = newUser();
        add(userId, "a", 0);
        add(userId, "b", 10);
        add(userId, "c", 20);
        add(otherUser, "other-1", 0);
        add(otherUser, "other-2", 10);
        add(otherUser, "other-3", 20);
        db.flushAndClear();

        history.keepNewest(userId, 2);
        db.flushAndClear();

        assertThat(history.findRecentHashes(userId, 10)).containsExactly("c", "b");
        assertThat(history.findRecentHashes(otherUser, 10)).containsExactly("other-3", "other-2", "other-1");
    }

    @Test
    void keepNewestDoesNothingWhenThereAreFewerEntries() {
        long userId = newUser();
        add(userId, "only", 0);
        db.flushAndClear();

        history.keepNewest(userId, 2);

        assertThat(history.findRecentHashes(userId, 10)).containsExactly("only");
        assertThat(jdbc.queryForObject(
                "select count(*) from password_history where user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void anEntryForAnUnknownAccountBecomesAnIllegalStateWithoutLeakingSpringTypes() {
        PasswordHistoryEntry orphan = PasswordHistoryEntry.of(Long.MAX_VALUE, "hash", NOW);

        assertThatThrownBy(() -> history.add(orphan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Password history entry rejected by constraint");
    }
}
