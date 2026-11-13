package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.Session;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.port.SessionRepository;
import org.kore.myapp.core.port.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.NOW;

@PersistenceAdapterTest
class JpaUserAndSessionRepositoryIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private PersistenceFixtures db;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findByUsernameFindsTheUserWithTheDatabaseCreationTime() {
        long id = db.insertUser("budi");
        String username = db.username(id);
        db.flushAndClear();

        User user = users.findByUsername(username).orElseThrow();
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getName()).isEqualTo("User " + username);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void findByUsernameIsEmptyForUnknownUsername() {
        assertThat(users.findByUsername("nobody")).isEmpty();
    }

    @Test
    void addPersistsEveryColumnOfTheSession() {
        long userId = db.insertUser("budi");
        Session session = Session.builder()
                .userId(userId)
                .publicId(UUID.fromString("00000000-0000-0000-0000-0000000000bb"))
                .tokenHash("a".repeat(64))
                .expiredAt(NOW.plusSeconds(10))
                .createdAt(NOW)
                .build();

        Session stored = sessions.add(session);
        db.flushAndClear();

        Session reloaded = db.find(Session.class, stored.getId());
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(session);
    }

    @Test
    void aSessionWithoutARevocationTimeLoadsAndStaysOpen() {
        long userId = db.insertUser("open");
        Session stored = sessions.add(Session.builder()
                .userId(userId).publicId(UUID.randomUUID()).tokenHash(hashOf("open"))
                .expiredAt(NOW.plusSeconds(10)).createdAt(NOW).build());
        db.flushAndClear();

        assertThat(db.find(Session.class, stored.getId()).getRevokedAt()).isNull();
    }

    @Test
    void revokeAllForUserEndsEveryOpenSessionOfThatUserOnly() {
        long userId = db.insertUser("revoke");
        long otherUserId = db.insertUser("bystander");
        long first = insertSession(userId, null);
        long second = insertSession(userId, null);
        Instant earlier = NOW.minusSeconds(3600);
        long alreadyRevoked = insertSession(userId, earlier);
        long othersSession = insertSession(otherUserId, null);
        db.flushAndClear();
        Instant at = NOW.plusSeconds(5);

        int ended = sessions.revokeAllForUser(userId, at);

        assertThat(ended).isEqualTo(2);
        assertThat(revokedAt(first)).isEqualTo(at);
        assertThat(revokedAt(second)).isEqualTo(at);
        assertThat(revokedAt(alreadyRevoked)).isEqualTo(earlier);
        assertThat(revokedAt(othersSession)).isNull();
    }

    @Test
    void revokeAllForUserReturnsZeroWhenTheUserHasNoOpenSessions() {
        long userId = db.insertUser("nosessions");
        db.flushAndClear();

        assertThat(sessions.revokeAllForUser(userId, NOW)).isZero();
    }

    private long insertSession(long userId, Instant revokedAt) {
        return jdbc.queryForObject("""
                insert into sessions (user_id, token_hash, expires_at, revoked_at)
                values (?, ?, ?, ?) returning id""", Long.class,
                userId, hashOf("session"), Timestamp.from(NOW.plusSeconds(3600)),
                revokedAt == null ? null : Timestamp.from(revokedAt));
    }

    private Instant revokedAt(long sessionId) {
        OffsetDateTime value = jdbc.queryForObject(
                "select revoked_at from sessions where id = ?", OffsetDateTime.class, sessionId);
        return value == null ? null : value.toInstant();
    }

    /** 64 characters, unique per call, as the CHAR(64) token_hash column requires. */
    private static String hashOf(String label) {
        String random = UUID.randomUUID().toString().replace("-", "");
        return (random + random).substring(0, 64);
    }
}
