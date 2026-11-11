package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.Session;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.port.SessionRepository;
import org.kore.myapp.core.port.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

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
}
