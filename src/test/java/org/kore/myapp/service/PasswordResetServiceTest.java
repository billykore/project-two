package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.PasswordHistoryEntry;
import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.CodeDeliveryException;
import org.kore.myapp.core.exception.InvalidPasswordResetException;
import org.kore.myapp.core.exception.InvalidResetCodeException;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordHasher;
import org.kore.myapp.core.port.PasswordHistoryRepository;
import org.kore.myapp.core.port.PasswordResetCodeRepository;
import org.kore.myapp.core.port.ResetCodeGenerator;
import org.kore.myapp.core.port.ResetCodeHasher;
import org.kore.myapp.core.port.ResetCodeSender;
import org.kore.myapp.core.port.SessionRepository;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.ContactChannel;
import org.kore.myapp.core.valueobject.FieldError;
import org.kore.myapp.core.valueobject.PasswordResetPolicy;
import org.kore.myapp.core.valueobject.RequestPasswordResetCommand;
import org.kore.myapp.core.valueobject.ResetPasswordCommand;
import org.kore.myapp.service.support.MutableClock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kore.myapp.service.support.Fixtures.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PasswordResetServiceTest {

    private static final long USER_ID = 42L;
    private static final Contact EMAIL = new Contact(ContactChannel.EMAIL, "budi@example.com");
    private static final Contact PHONE = new Contact(ContactChannel.PHONE, "+6281234567890");
    private static final Duration TTL = Duration.ofMinutes(5);

    private UserRepository users;
    private PasswordResetCodeRepository codes;
    private PasswordHasher passwordHasher;
    private PasswordHistoryRepository history;
    private SessionRepository sessions;
    private ResetCodeHasher codeHasher;
    private ResetCodeSender sender;
    private PasswordResetService service;
    private User user;
    private MutableClock clock;
    private AtomicInteger generatedCodes;
    private FakeCodeRepository fakeCodes;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        codes = mock(PasswordResetCodeRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        history = mock(PasswordHistoryRepository.class);
        sessions = mock(SessionRepository.class);
        codeHasher = mock(ResetCodeHasher.class);
        sender = mock(ResetCodeSender.class);
        generatedCodes = new AtomicInteger();
        ResetCodeGenerator generator = () -> {
            generatedCodes.incrementAndGet();
            return "004213";
        };
        PasswordResetPolicy policy = new PasswordResetPolicy(TTL, 5, Duration.ofSeconds(60), 5);
        clock = new MutableClock(NOW);
        ResetCodeIssuer issuer = new ResetCodeIssuer(users, codes, policy);
        service = new PasswordResetService(users, codes, history, sessions, passwordHasher, codeHasher, generator, sender,
                issuer, policy, clock);
        fakeCodes = null;

        user = User.builder().id(USER_ID).username("budi").password("old-hash")
                .name("Budi").email("budi@example.com").phone("+6281234567890").build();
        when(codeHasher.hash("004213")).thenReturn("code-hash");
        when(passwordHasher.hash("newsecret99")).thenReturn("new-hash");
        when(codes.add(any(PasswordResetCode.class))).thenAnswer(call -> call.getArgument(0));
    }

    // --- requestReset ---------------------------------------------------------------------------------------

    @Test
    void requestForAKnownEmailStoresAHashedCodeThenSendsTheCodeAfterwards() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.of(user));

        service.requestReset(new RequestPasswordResetCommand("budi@example.com", null));

        ArgumentCaptor<PasswordResetCode> stored = ArgumentCaptor.forClass(PasswordResetCode.class);
        InOrder order = inOrder(codes, sender);
        order.verify(codes).add(stored.capture());
        order.verify(sender).send(EMAIL, "004213");
        assertThat(stored.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getValue().getCodeHash()).isEqualTo("code-hash").doesNotContain("004213");
        assertThat(stored.getValue().getChannel()).isEqualTo(ContactChannel.EMAIL);
        assertThat(stored.getValue().getIssuedAt()).isEqualTo(NOW);
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void requestForAKnownPhoneUsesThePhoneChannel() {
        when(users.findForPasswordReset(PHONE)).thenReturn(Optional.of(user));

        service.requestReset(new RequestPasswordResetCommand(null, "+6281234567890"));

        ArgumentCaptor<PasswordResetCode> stored = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codes).add(stored.capture());
        assertThat(stored.getValue().getChannel()).isEqualTo(ContactChannel.PHONE);
        verify(sender).send(PHONE, "004213");
    }

    @Test
    void requestForAnUnknownContactStoresAndSendsNothingAndDoesNotFail() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.empty());

        assertThatCode(() -> service.requestReset(new RequestPasswordResetCommand("budi@example.com", null)))
                .doesNotThrowAnyException();

        verify(codes, never()).add(any());
        verify(sender, never()).send(any(), any());
    }

    @Test
    void aDeliveryFailureIsHandledAndTheRequestStillCompletes() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.of(user));
        doThrow(new CodeDeliveryException("no channel")).when(sender).send(EMAIL, "004213");

        assertThatCode(() -> service.requestReset(new RequestPasswordResetCommand("budi@example.com", null)))
                .doesNotThrowAnyException();

        verify(codes).add(any(PasswordResetCode.class));
    }

    @Test
    void whenTheAccountIsBusyTheRequestCompletesAndSendsNothing() {
        when(users.findForPasswordReset(EMAIL)).thenThrow(new PasswordResetBusyException());

        assertThatCode(() -> service.requestReset(new RequestPasswordResetCommand("budi@example.com", null)))
                .doesNotThrowAnyException();

        verify(codes, never()).add(any());
        verify(sender, never()).send(any(), any());
    }

    // --- resetPassword --------------------------------------------------------------------------------------

    @Test
    void theRightCodeChangesThePasswordUsesUpTheCodeAndSavesBoth() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(code.getUsedAt()).isEqualTo(NOW);
        verify(users).update(user);
        verify(codes).update(code);
    }

    @Test
    void thePhoneWorksEvenWhenTheCodeWasSentByEmail() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(PHONE, code, true);

        service.resetPassword(new ResetPasswordCommand(null, "+6281234567890", "004213", "newsecret99"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(code.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void theEmailWorksEvenWhenTheCodeWasSentByPhone() {
        PasswordResetCode code = openCode(ContactChannel.PHONE);
        givenAccountWithCode(EMAIL, code, true);

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void anUnknownAccountIsRefusedAsAnInvalidCode() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class);

        verify(passwordHasher, never()).hash(any());
        verify(users, never()).update(any());
    }

    @Test
    void anAccountWithoutAnOpenCodeIsRefused() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.of(user));
        when(codes.findOpen(USER_ID)).thenReturn(Optional.empty());

        assertRefusedWithPasswordUnchanged();
    }

    @Test
    void anExpiredCodeIsRefusedEvenWhenItIsTheRightOne() {
        PasswordResetCode expired = PasswordResetCode.issue(
                USER_ID, "code-hash", ContactChannel.EMAIL, NOW.minus(TTL).minusSeconds(1), TTL);
        givenAccountWithCode(EMAIL, expired, true);

        assertRefusedWithPasswordUnchanged();
        assertThat(expired.getUsedAt()).isNull();
    }

    @Test
    void aWrongCodeIsRefusedAndNothingChanges() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, false);

        assertRefusedWithPasswordUnchanged();
        assertThat(code.getUsedAt()).isNull();
    }

    // --- helpers --------------------------------------------------------------------------------------------

    private PasswordResetCode openCode(ContactChannel channel) {
        return PasswordResetCode.issue(USER_ID, "code-hash", channel, NOW.minusSeconds(30), TTL);
    }

    private void givenAccountWithCode(Contact contact, PasswordResetCode code, boolean codeMatches) {
        when(users.findForPasswordReset(contact)).thenReturn(Optional.of(user));
        when(codes.findOpen(USER_ID)).thenReturn(Optional.of(code));
        when(codeHasher.matches("004213", "code-hash")).thenReturn(codeMatches);
    }

    private void assertRefusedAsRecentlyUsed() {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOfSatisfying(InvalidPasswordResetException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldError(
                                "newPassword", "must not be one of your last 3 passwords")));
    }

    private void assertCodeStillOpenAndNothingSaved(PasswordResetCode code) {
        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getFailedAttempts()).isZero();
        assertThat(code.isUsable(clock.instant())).isTrue();
        assertThat(user.getPassword()).isEqualTo("old-hash");
        verify(users, never()).update(any());
        verify(history, never()).add(any());
    }

    private void assertRefusedWithPasswordUnchanged() {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class)
                .hasMessage("Invalid or expired code");
        assertThat(user.getPassword()).isEqualTo("old-hash");
        verify(users, never()).update(any());
    }

    // --- limits, superseding, and attempts (US2) --------------------------------------------------------------

    @Test
    void aSecondRequestWithinTheResendIntervalStoresAndSendsNothing() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        clock.advance(Duration.ofSeconds(59));

        requestAt(EMAIL, Duration.ZERO);

        assertThat(fakeCodes.stored).hasSize(1);
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void aRequestExactlyOneResendIntervalAfterTheLastIsAllowed() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);

        requestAt(EMAIL, Duration.ofSeconds(60));

        assertThat(fakeCodes.stored).hasSize(2);
    }

    @Test
    void theSixthRequestWithinAnHourSendsNothingButTheFifthIsAllowed() {
        useFakeCodes();
        for (int i = 0; i < 5; i++) {
            requestAt(EMAIL, i == 0 ? Duration.ZERO : Duration.ofSeconds(60));
        }
        assertThat(fakeCodes.stored).hasSize(5);

        requestAt(EMAIL, Duration.ofSeconds(60));

        assertThat(fakeCodes.stored).hasSize(5);
        verify(sender, times(5)).send(any(), any());
    }

    @Test
    void theHourlyLimitFreesUpOnceTheEarliestCodeIsMoreThanAnHourOld() {
        useFakeCodes();
        for (int i = 0; i < 5; i++) {
            requestAt(EMAIL, i == 0 ? Duration.ZERO : Duration.ofSeconds(60));
        }

        requestAt(EMAIL, Duration.ofHours(1));

        assertThat(fakeCodes.stored).hasSize(6);
    }

    @Test
    void aNewRequestInvalidatesThePreviousOpenCodeBeforeStoringTheNewOne() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        PasswordResetCode first = fakeCodes.stored.get(0);

        requestAt(EMAIL, Duration.ofSeconds(61));

        PasswordResetCode second = fakeCodes.stored.get(1);
        assertThat(first.getInvalidatedAt()).isEqualTo(NOW.plusSeconds(61));
        assertThat(first.isUsable(clock.instant())).isFalse();
        assertThat(second.isUsable(clock.instant())).isTrue();
    }

    @Test
    void anExpiredButStillOpenCodeIsInvalidatedSoANewOneCanBeStored() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);

        requestAt(EMAIL, TTL.plusMinutes(1));

        assertThat(fakeCodes.stored).hasSize(2);
        assertThat(fakeCodes.stored.get(0).getInvalidatedAt()).isNotNull();
    }

    @Test
    void anUnknownContactStillGeneratesAndHashesExactlyOneCode() {
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(new RequestPasswordResetCommand("budi@example.com", null));

        assertThat(generatedCodes.get()).isEqualTo(1);
        verify(codeHasher, times(1)).hash("004213");
    }

    @Test
    void aLimitedRequestStillGeneratesAndHashesExactlyOneCode() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        generatedCodes.set(0);
        clearInvocations(codeHasher);

        requestAt(EMAIL, Duration.ofSeconds(1));

        assertThat(generatedCodes.get()).isEqualTo(1);
        verify(codeHasher, times(1)).hash("004213");
    }

    @Test
    void aWrongCodeIsCountedAndSaved() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        PasswordResetCode code = fakeCodes.stored.get(0);

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "111111", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class);

        assertThat(code.getFailedAttempts()).isEqualTo(1);
        assertThat(code.isUsable(clock.instant())).isTrue();
        assertThat(user.getPassword()).isEqualTo("old-hash");
    }

    @Test
    void afterTheFifthWrongCodeTheRightOneIsRefusedToo() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        PasswordResetCode code = fakeCodes.stored.get(0);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> service.resetPassword(
                    new ResetPasswordCommand("budi@example.com", null, "111111", "newsecret99")))
                    .isInstanceOf(InvalidResetCodeException.class);
        }

        assertThat(code.getFailedAttempts()).isEqualTo(5);
        assertThat(code.getInvalidatedAt()).isNotNull();
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
    }

    @Test
    void aUsedCodeCannotBeUsedAgain() {
        useFakeCodes();
        requestAt(EMAIL, Duration.ZERO);
        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class);
    }

    @Test
    void aSupersededCodeIsRefusedAndOnlyTheNewestWorks() {
        useFakeCodes("111111", "222222");
        requestAt(EMAIL, Duration.ZERO);
        requestAt(EMAIL, Duration.ofSeconds(61));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "111111", "newsecret99")))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "222222", "newsecret99"));
        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void resetPasswordKeepsTheFailedAttemptByNotRollingBackForAnInvalidCode() throws NoSuchMethodException {
        Transactional transactional = PasswordResetService.class
                .getMethod("resetPassword", ResetPasswordCommand.class).getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor()).containsExactly(InvalidResetCodeException.class);
    }

    // --- username rule and password history (US3) -------------------------------------------------------------

    @Test
    void aNewPasswordEqualToTheUsernameIsRefusedAndTheCodeStaysOpen() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);
        user = User.builder().id(USER_ID).username("newsecret99").password("old-hash")
                .email("budi@example.com").build();
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99")))
                .isInstanceOfSatisfying(InvalidPasswordResetException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldError(
                                "newPassword", "must not be the same as the username")));

        assertCodeStillOpenAndNothingSaved(code);
    }

    @Test
    void aNewPasswordEqualToTheCurrentOneIsRefused() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);
        when(passwordHasher.compare("newsecret99", "old-hash")).thenReturn(true);

        assertRefusedAsRecentlyUsed();

        assertCodeStillOpenAndNothingSaved(code);
    }

    @Test
    void aNewPasswordEqualToOneOfTheTwoBeforeTheCurrentOneIsRefused() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);
        when(history.findRecentHashes(USER_ID, 2)).thenReturn(List.of("newer-old-hash", "older-old-hash"));
        when(passwordHasher.compare("newsecret99", "older-old-hash")).thenReturn(true);

        assertRefusedAsRecentlyUsed();

        assertCodeStillOpenAndNothingSaved(code);
    }

    @Test
    void anUnusedPasswordSucceedsRecordsTheReplacedHashAndKeepsOnlyTwoEntries() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);
        when(history.findRecentHashes(USER_ID, 2)).thenReturn(List.of("newer-old-hash", "older-old-hash"));

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        ArgumentCaptor<PasswordHistoryEntry> entry = ArgumentCaptor.forClass(PasswordHistoryEntry.class);
        InOrder order = inOrder(history);
        order.verify(history).add(entry.capture());
        order.verify(history).keepNewest(USER_ID, 2);
        assertThat(entry.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(entry.getValue().getPasswordHash()).isEqualTo("old-hash");
        assertThat(entry.getValue().getReplacedAt()).isEqualTo(NOW);
        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void withFewerThanTwoHistoryEntriesOnlyTheKnownOnesAreCompared() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);
        when(history.findRecentHashes(USER_ID, 2)).thenReturn(List.of("only-old-hash"));

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        verify(passwordHasher).compare("newsecret99", "only-old-hash");
        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void theHistoryIsNeverReadForAWrongCode() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, false);

        assertRefusedWithPasswordUnchanged();

        verify(history, never()).findRecentHashes(anyLong(),
                anyInt());
    }

    // --- sessions end on a successful reset (US4) --------------------------------------------------------------

    @Test
    void aSuccessfulResetEndsAllSessionsOfTheAccountOnce() {
        PasswordResetCode code = openCode(ContactChannel.EMAIL);
        givenAccountWithCode(EMAIL, code, true);

        service.resetPassword(new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"));

        verify(sessions, times(1)).revokeAllForUser(USER_ID, NOW);
    }

    @Test
    void aWrongCodeNeverEndsSessions() {
        givenAccountWithCode(EMAIL, openCode(ContactChannel.EMAIL), false);

        assertRefusedWithPasswordUnchanged();

        verify(sessions, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void aRefusedPasswordNeverEndsSessions() {
        givenAccountWithCode(EMAIL, openCode(ContactChannel.EMAIL), true);
        when(passwordHasher.compare("newsecret99", "old-hash")).thenReturn(true);

        assertRefusedAsRecentlyUsed();

        verify(sessions, never()).revokeAllForUser(anyLong(), any());
    }

    // --- US2 helpers ------------------------------------------------------------------------------------------

    /** Replaces the Mockito code repository with an in-memory one that mirrors the real counting rules. */
    private void useFakeCodes(String... codesToGenerate) {
        PasswordResetPolicy policy = new PasswordResetPolicy(TTL, 5, Duration.ofSeconds(60), 5);
        fakeCodes = new FakeCodeRepository();
        when(users.findForPasswordReset(EMAIL)).thenReturn(Optional.of(user));
        when(codeHasher.hash(any())).thenAnswer(call -> "h-" + call.getArgument(0));
        when(codeHasher.matches(any(), any()))
                .thenAnswer(call -> ("h-" + call.getArgument(0)).equals(call.getArgument(1)));
        String[] sequence = codesToGenerate.length == 0 ? new String[]{"004213"} : codesToGenerate;
        AtomicInteger next = new AtomicInteger();
        ResetCodeIssuer issuer = new ResetCodeIssuer(users, fakeCodes, policy);
        service = new PasswordResetService(users, fakeCodes, history, sessions, passwordHasher, codeHasher, () -> {
            generatedCodes.incrementAndGet();
            return sequence[Math.min(next.getAndIncrement(), sequence.length - 1)];
        }, sender, issuer, policy, clock);
    }

    private void requestAt(Contact contact, Duration afterNow) {
        clock.advance(afterNow);
        RequestPasswordResetCommand command = contact.channel() == ContactChannel.EMAIL
                ? new RequestPasswordResetCommand(contact.address(), null)
                : new RequestPasswordResetCommand(null, contact.address());
        service.requestReset(command);
    }

    /** In-memory {@link PasswordResetCodeRepository}; refuses a second open code like the database index does. */
    private static final class FakeCodeRepository implements PasswordResetCodeRepository {

        private final List<PasswordResetCode> stored = new ArrayList<>();

        @Override
        public PasswordResetCode add(PasswordResetCode code) {
            if (findOpen(code.getUserId()).isPresent()) {
                throw new PasswordResetBusyException();
            }
            stored.add(code);
            return code;
        }

        @Override
        public PasswordResetCode update(PasswordResetCode code) {
            return code;
        }

        @Override
        public Optional<PasswordResetCode> findOpen(long userId) {
            return stored.stream()
                    .filter(c -> c.getUserId() == userId && c.getUsedAt() == null && c.getInvalidatedAt() == null)
                    .findFirst();
        }

        @Override
        public long countIssuedSince(long userId, Instant since) {
            return stored.stream()
                    .filter(c -> c.getUserId() == userId && !c.getIssuedAt().isBefore(since))
                    .count();
        }
    }
}
