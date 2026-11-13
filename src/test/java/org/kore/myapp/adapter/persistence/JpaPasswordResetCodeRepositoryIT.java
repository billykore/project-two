package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordResetCodeRepository;
import org.kore.myapp.core.valueobject.ContactChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JpaPasswordResetCodeRepository} against PostgreSQL with the real Flyway migrations.
 */
@PersistenceAdapterTest
class JpaPasswordResetCodeRepositoryIT {

    private static final Instant NOW = PersistenceFixtures.NOW;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String HASH = "b".repeat(64);

    @Autowired
    private PasswordResetCodeRepository codes;

    @Autowired
    private PersistenceFixtures db;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long digits = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        return db.insertUser("reset", "reset-" + suffix + "@example.com", "+62" + digits);
    }

    private PasswordResetCode codeFor(long userId, Instant issuedAt) {
        return PasswordResetCode.issue(userId, HASH, ContactChannel.EMAIL, issuedAt, TTL);
    }

    @Test
    void addStoresEveryColumnAndFindOpenReturnsTheCode() {
        long userId = newUser();

        PasswordResetCode saved = codes.add(codeFor(userId, NOW));
        db.flushAndClear();

        PasswordResetCode found = codes.findOpen(userId).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getCodeHash()).isEqualTo(HASH);
        assertThat(found.getChannel()).isEqualTo(ContactChannel.EMAIL);
        assertThat(found.getIssuedAt()).isEqualTo(NOW);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(found.getFailedAttempts()).isZero();
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getInvalidatedAt()).isNull();
    }

    @Test
    void findOpenIsEmptyForAnAccountWithoutCodes() {
        assertThat(codes.findOpen(newUser())).isEmpty();
    }

    @Test
    void findOpenIgnoresAUsedCode() {
        long userId = newUser();
        PasswordResetCode code = codes.add(codeFor(userId, NOW));

        code.markUsed(NOW.plusSeconds(10));
        codes.update(code);
        db.flushAndClear();

        assertThat(codes.findOpen(userId)).isEmpty();
        assertThat(db.find(PasswordResetCode.class, code.getId()).getUsedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void findOpenIgnoresOtherAccountsCodes() {
        long mine = newUser();
        long theirs = newUser();
        codes.add(codeFor(theirs, NOW));
        db.flushAndClear();

        assertThat(codes.findOpen(mine)).isEmpty();
        assertThat(codes.findOpen(theirs)).isPresent();
    }

    @Test
    void countIssuedSinceCountsByAccountAndTimeIncludingTheBoundary() {
        long userId = newUser();
        long otherUser = newUser();
        db.insertResetCode(userId, NOW.minus(Duration.ofHours(2)), false);
        db.insertResetCode(userId, NOW.minus(Duration.ofHours(1)), false);
        db.insertResetCode(userId, NOW.minus(Duration.ofMinutes(10)), false);
        db.insertResetCode(userId, NOW, true);
        db.insertResetCode(otherUser, NOW, true);
        db.flushAndClear();

        assertThat(codes.countIssuedSince(userId, NOW.minus(Duration.ofHours(1)))).isEqualTo(3);
        assertThat(codes.countIssuedSince(userId, NOW.minus(Duration.ofHours(1)).plusMillis(1))).isEqualTo(2);
        assertThat(codes.countIssuedSince(userId, NOW)).isEqualTo(1);
        assertThat(codes.countIssuedSince(userId, NOW.plusSeconds(1))).isZero();
        assertThat(codes.countIssuedSince(otherUser, NOW.minus(Duration.ofHours(1)))).isEqualTo(1);
    }

    @Test
    void aSecondOpenCodeForTheSameAccountIsReportedAsBusy() {
        long userId = newUser();
        codes.add(codeFor(userId, NOW));

        assertThatThrownBy(() -> codes.add(codeFor(userId, NOW.plusSeconds(1))))
                .isInstanceOf(PasswordResetBusyException.class);
    }

    @Test
    void anotherCodeIsAllowedOnceTheFirstIsUsed() {
        long userId = newUser();
        PasswordResetCode first = codes.add(codeFor(userId, NOW));
        first.markUsed(NOW.plusSeconds(5));
        codes.update(first);
        db.flushAndClear();

        PasswordResetCode second = codes.add(codeFor(userId, NOW.plusSeconds(10)));

        assertThat(codes.findOpen(userId).orElseThrow().getId()).isEqualTo(second.getId());
    }

    /** Only the open-code index is a business outcome; any other violation is a bug and says which constraint. */
    @Test
    void otherIntegrityViolationsBecomeAnIllegalStateWithoutLeakingSpringTypes() {
        PasswordResetCode orphan = codeFor(Long.MAX_VALUE, NOW);

        assertThatThrownBy(() -> codes.add(orphan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Password reset code rejected by constraint");
    }

    @Test
    void anIntegrityViolationDuringUpdateBecomesAnIllegalStateWithoutLeakingSpringTypes() {
        PasswordResetCode orphan = codeFor(Long.MAX_VALUE, NOW);

        assertThatThrownBy(() -> codes.update(orphan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Password reset code rejected by constraint");
    }

    /**
     * The account lock normally prevents this race; the partial unique index is the safety net behind it. Two
     * transactions each add an open code for the same account at the same moment: exactly one is stored, and the
     * other is reported as busy.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsOfAnOpenCodeForOneAccountLetExactlyOneSucceed() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long userId = jdbc.queryForObject("""
                insert into users (name, username, password, email, phone)
                values ('Race', ?, 'hash', ?, ?) returning id""", Long.class,
                "race-" + suffix, "race-" + suffix + "@example.com",
                "+62" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Long> stored = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Instant issuedAt = NOW.plusSeconds(i);
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        stored.add(tx.execute(status -> codes.add(codeFor(userId, issuedAt)).getId()));
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
            assertThat(failures).singleElement().isInstanceOf(PasswordResetBusyException.class);
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from users where id = ?", userId);
        }
    }
}
