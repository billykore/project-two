package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.RegisterUserCommand;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registration-related behavior of {@link JpaUserRepository} against PostgreSQL. The existing lookup tests stay in
 * {@link JpaUserAndSessionRepositoryIT}.
 */
@PersistenceAdapterTest
class JpaUserRepositoryIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private PersistenceFixtures db;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    /** A new user whose username, email, and phone are all unique to this call. */
    private static User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return user("budi-" + suffix, "budi-" + suffix + "@example.com", newPhone());
    }

    private static User user(String username, String email, String phone) {
        return User.register(new RegisterUserCommand(username, "secret123", "Budi Santoso", email, phone), "bcrypt-hash");
    }

    private static String newPhone() {
        return "+62" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
    }

    @Test
    void addPersistsEveryColumnAndAssignsIdAndCreationTime() {
        User user = newUser();

        User saved = users.add(user);
        db.flushAndClear();

        User reloaded = db.find(User.class, saved.getId());
        assertThat(saved.getId()).isNotNull();
        assertThat(reloaded.getUsername()).isEqualTo(user.getUsername());
        assertThat(reloaded.getName()).isEqualTo("Budi Santoso");
        assertThat(reloaded.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(reloaded.getEmail()).isEqualTo(user.getEmail());
        assertThat(reloaded.getPhone()).isEqualTo(user.getPhone());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void usersWithoutEmailOrPhoneCoexist() {
        assertThatCode(() -> {
            db.insertUser("legacy-one");
            db.insertUser("legacy-two");
            db.flushAndClear();
        }).doesNotThrowAnyException();
    }

    @Test
    void existsByReportsOnlyValuesThatBelongToAnAccount() {
        User saved = users.add(newUser());

        assertThat(users.existsByUsername(saved.getUsername())).isTrue();
        assertThat(users.existsByEmail(saved.getEmail())).isTrue();
        assertThat(users.existsByPhone(saved.getPhone())).isTrue();
        assertThat(users.existsByUsername("nobody-" + UUID.randomUUID())).isFalse();
        assertThat(users.existsByEmail("nobody-" + UUID.randomUUID() + "@example.com")).isFalse();
        assertThat(users.existsByPhone(newPhone())).isFalse();
    }

    @Test
    void addReportsADuplicateUsername() {
        User existing = users.add(newUser());
        User duplicate = user(existing.getUsername(), "other-" + UUID.randomUUID() + "@example.com", newPhone());

        assertThatThrownBy(() -> users.add(duplicate)).isInstanceOfSatisfying(RegistrationConflictException.class,
                e -> assertThat(e.getFields()).containsExactly(RegistrationField.USERNAME));
    }

    @Test
    void addReportsADuplicateEmail() {
        User existing = users.add(newUser());
        User duplicate = user("other-" + UUID.randomUUID(), existing.getEmail(), newPhone());

        assertThatThrownBy(() -> users.add(duplicate)).isInstanceOfSatisfying(RegistrationConflictException.class,
                e -> assertThat(e.getFields()).containsExactly(RegistrationField.EMAIL));
    }

    @Test
    void addReportsADuplicatePhone() {
        User existing = users.add(newUser());
        User duplicate = user("other-" + UUID.randomUUID(), "other-" + UUID.randomUUID() + "@example.com",
                existing.getPhone());

        assertThatThrownBy(() -> users.add(duplicate)).isInstanceOfSatisfying(RegistrationConflictException.class,
                e -> assertThat(e.getFields()).containsExactly(RegistrationField.PHONE));
    }

    /** Only the three uniqueness rules are business conflicts; any other integrity violation propagates. */
    @Test
    void addRethrowsOtherIntegrityViolationsUntranslated() {
        User mixedCaseEmail = User.builder().username("mixed-" + UUID.randomUUID()).password("bcrypt-hash")
                .name("Mixed").email("Mixed@Example.com").phone(newPhone()).build();

        assertThatThrownBy(() -> users.add(mixedCaseEmail))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(RegistrationConflictException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsOfOneUsernameLetExactlyOneSucceed() throws Exception {
        User first = newUser();
        User second = user(first.getUsername(), "other-" + UUID.randomUUID() + "@example.com", newPhone());

        race(first, second, RegistrationField.USERNAME);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsOfOneEmailLetExactlyOneSucceed() throws Exception {
        User first = newUser();
        User second = user("other-" + UUID.randomUUID(), first.getEmail(), newPhone());

        race(first, second, RegistrationField.EMAIL);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsOfOnePhoneLetExactlyOneSucceed() throws Exception {
        User first = newUser();
        User second = user("other-" + UUID.randomUUID(), "other-" + UUID.randomUUID() + "@example.com",
                first.getPhone());

        race(first, second, RegistrationField.PHONE);
    }

    /**
     * Adds both users at the same moment, each in its own committed transaction, and expects exactly one to win and
     * the other to be reported as a conflict on {@code field}.
     */
    private void race(User first, User second, RegistrationField field) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Long> stored = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (User user : List.of(first, second)) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        stored.add(tx.execute(status -> users.add(user).getId()));
                    } catch (RuntimeException e) {
                        failures.add(e);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }

            assertThat(stored).hasSize(1);
            assertThat(failures).singleElement().isInstanceOfSatisfying(RegistrationConflictException.class,
                    e -> assertThat(e.getFields()).containsExactly(field));
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from users where username in (?, ?)", first.getUsername(), second.getUsername());
        }
    }
}
