package org.kore.myapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kore.myapp.RegistrationFlowIT.Applicant;
import org.kore.myapp.core.port.ResetCodeSender;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.service.support.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Password reset end to end against the real application, PostgreSQL, and the real security configuration.
 * <p>
 * The delivery is replaced by a recording sender, so a test can read the code a user would have received. As in
 * {@link RegistrationFlowIT}, every test creates its own applicant and never relies on another test's data.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetFlowIT {

    private static final String OLD_PASSWORD = "secret123";
    private static final String NEW_PASSWORD = "newsecret99";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ResetCodeSender sender;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromTheRealTime() {
        clock.set(Instant.now());
    }

    /** Remembers the latest code sent to each address, instead of delivering it. */
    static class RecordingResetCodeSender implements ResetCodeSender {

        private final Map<String, String> latestCodeByAddress = new ConcurrentHashMap<>();

        @Override
        public void send(Contact contact, String code) {
            latestCodeByAddress.put(contact.address(), code);
        }

        String latestCodeFor(String address) {
            return latestCodeByAddress.get(address);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingSenderConfiguration {

        @Bean
        @Primary
        RecordingResetCodeSender recordingResetCodeSender() {
            return new RecordingResetCodeSender();
        }

        /** Replaces the system clock, so a test can move past a code's expiry without waiting. */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Test
    void theInjectedSenderIsTheRecordingOne() {
        assertThat(sender).isInstanceOf(RecordingResetCodeSender.class);
    }

    @Test
    void resetByEmailChangesThePasswordSoOnlyTheNewOneLogsIn() throws Exception {
        Applicant applicant = registered("byemail");

        requestByEmail(applicant.email())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());
        String code = codeFor(applicant.email());
        assertThat(code).matches("[0-9]{6}");

        confirmByEmail(applicant.email(), code, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        login(applicant.username(), NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void resetByPhoneChangesThePasswordSoOnlyTheNewOneLogsIn() throws Exception {
        Applicant applicant = registered("byphone");

        requestByPhone(applicant.phone()).andExpect(status().isAccepted());
        String code = codeFor(applicant.phone());

        confirmByPhone(applicant.phone(), code, NEW_PASSWORD).andExpect(status().isNoContent());

        login(applicant.username(), NEW_PASSWORD).andExpect(status().isOk());
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void aCodeSentByEmailCanBeConfirmedWithThePhoneOfTheSameAccount() throws Exception {
        Applicant applicant = registered("crosschannel");

        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());

        confirmByPhone(applicant.phone(), code, NEW_PASSWORD).andExpect(status().isNoContent());

        login(applicant.username(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aRequestForAnUnknownContactGetsTheSameAnswerAsAKnownOneAndSendsNothing() throws Exception {
        Applicant applicant = registered("known");
        String unknownEmail = "nobody." + System.nanoTime() + "@example.com";

        String known = requestByEmail(applicant.email())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = requestByEmail(unknownEmail)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        assertThat(codeFor(applicant.email())).isNotNull();
        assertThat(((RecordingResetCodeSender) sender).latestCodeFor(unknownEmail)).isNull();
    }

    @Test
    void aWrongCodeLeavesThePasswordUnchanged() throws Exception {
        Applicant applicant = registered("wrongcode");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String wrong = "000000".equals(code) ? "000001" : "000000";

        confirmByEmail(applicant.email(), wrong, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid or expired code"));

        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
        login(applicant.username(), NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    // --- codes cannot be guessed, reused, or abused ------------------------------------------------------------

    @Test
    void fiveWrongCodesInvalidateTheCodeSoEvenTheRightOneIsRefused() throws Exception {
        Applicant applicant = registered("guess");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String wrong = "000000".equals(code) ? "000001" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            confirmByEmail(applicant.email(), wrong, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isBadRequest());

        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
        assertThat(failedAttempts(applicant)).isEqualTo(5);
    }

    @Test
    void aWrongCodeIsRememberedEvenThoughTheRequestFails() throws Exception {
        Applicant applicant = registered("remember");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String wrong = "000000".equals(code) ? "000001" : "000000";

        confirmByEmail(applicant.email(), wrong, NEW_PASSWORD).andExpect(status().isBadRequest());

        assertThat(failedAttempts(applicant)).isEqualTo(1);
        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aCodeIsRefusedOnceFiveMinutesHavePassed() throws Exception {
        Applicant applicant = registered("expiry");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());

        clock.advance(Duration.ofMinutes(5));
        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isBadRequest());

        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aCodeIsStillAcceptedJustBeforeItExpires() throws Exception {
        Applicant applicant = registered("lastminute");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));

        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aCodeCanBeUsedOnlyOnce() throws Exception {
        Applicant applicant = registered("once");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());

        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
        confirmByEmail(applicant.email(), code, "another-secret-1").andExpect(status().isBadRequest());

        login(applicant.username(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aNewRequestSupersedesTheEarlierCode() throws Exception {
        Applicant applicant = registered("supersede");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String first = codeFor(applicant.email());
        clock.advance(Duration.ofSeconds(61));
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String second = codeFor(applicant.email());

        if (!first.equals(second)) {
            confirmByEmail(applicant.email(), first, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        confirmByEmail(applicant.email(), second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aSecondRequestWithinSixtySecondsSendsNoNewCode() throws Exception {
        Applicant applicant = registered("resend");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String first = codeFor(applicant.email());

        clock.advance(Duration.ofSeconds(30));
        requestByEmail(applicant.email())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(codeFor(applicant.email())).isEqualTo(first);
        assertThat(issuedCodes(applicant)).isEqualTo(1);
    }

    @Test
    void theSixthCodeWithinAnHourIsNotSent() throws Exception {
        Applicant applicant = registered("hourly");

        for (int i = 0; i < 5; i++) {
            requestByEmail(applicant.email()).andExpect(status().isAccepted());
            clock.advance(Duration.ofSeconds(61));
        }
        assertThat(issuedCodes(applicant)).isEqualTo(5);

        requestByEmail(applicant.email()).andExpect(status().isAccepted());

        assertThat(issuedCodes(applicant)).isEqualTo(5);
    }

    @Test
    void everyKindOfCodeFailureGetsTheSameResponse() throws Exception {
        Applicant applicant = registered("samebody");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String wrong = "000000".equals(code) ? "000001" : "000000";

        String wrongBody = confirmByEmail(applicant.email(), wrong, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String unknownBody = confirmByEmail("nobody." + System.nanoTime() + "@example.com", code, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
        String usedBody = confirmByEmail(applicant.email(), code, NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        clock.advance(Duration.ofSeconds(61));
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(6));
        String expiredBody = confirmByEmail(applicant.email(), codeFor(applicant.email()), "yet-another-pw-1")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(List.of(unknownBody, usedBody, expiredBody))
                .allSatisfy(body -> assertThat(body).isEqualTo(wrongBody));
    }

    @Test
    void twoSimultaneousConfirmsWithOneCodeChangeThePasswordExactlyOnce() throws Exception {
        Applicant applicant = registered("concurrent");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String[] passwords = {"concurrent-one-1", "concurrent-two-2"};
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (String password : passwords) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    statuses.add(confirmByEmail(applicant.email(), code, password)
                            .andReturn().getResponse().getStatus());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).filteredOn(status -> status == 204).hasSize(1);
        assertThat(statuses).filteredOn(status -> status != 204).allMatch(status -> status == 400 || status == 503);
        int accepted = 0;
        for (String password : passwords) {
            if (login(applicant.username(), password).andReturn().getResponse().getStatus() == 200) {
                accepted++;
            }
        }
        assertThat(accepted).isEqualTo(1);
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isUnauthorized());
        assertThat(usedCodes(applicant)).isEqualTo(1);
        assertThat(historyEntries(applicant)).isEqualTo(1);
    }

    // --- new password rules and history -------------------------------------------------------------------------

    @Test
    void theLastThreePasswordsAreRefusedAndTheOldestBecomesAllowedAgain() throws Exception {
        Applicant applicant = registered("history");
        resetTo(applicant, "pass-two-2");
        resetTo(applicant, "pass-three-3");
        String code = requestNewCode(applicant);

        for (String recent : new String[]{"pass-three-3", "pass-two-2", OLD_PASSWORD}) {
            confirmByEmail(applicant.email(), code, recent)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].message").value("must not be one of your last 3 passwords"));
        }
        confirmByEmail(applicant.email(), code, "pass-four-4").andExpect(status().isNoContent());

        login(applicant.username(), "pass-four-4").andExpect(status().isOk());
        assertThat(historyEntries(applicant)).isEqualTo(2);
        resetTo(applicant, OLD_PASSWORD);
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aRefusedPasswordKeepsTheCodeUsableAndIsNotCountedAsAWrongCode() throws Exception {
        Applicant applicant = registered("refused");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());

        confirmByEmail(applicant.email(), code, "short12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at least 8 characters"));
        confirmByEmail(applicant.email(), code, applicant.username())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not be the same as the username"));
        confirmByEmail(applicant.email(), code, OLD_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not be one of your last 3 passwords"));

        assertThat(failedAttempts(applicant)).isZero();
        confirmByEmail(applicant.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aResetWritesExactlyOneHistoryEntryHoldingTheReplacedPassword() throws Exception {
        Applicant applicant = registered("onehistory");
        requestByEmail(applicant.email()).andExpect(status().isAccepted());

        confirmByEmail(applicant.email(), codeFor(applicant.email()), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(historyEntries(applicant)).isEqualTo(1);
    }

    // --- resetting signs out everywhere ---------------------------------------------------------------------------

    @Test
    void aResetEndsEverySessionAndALaterLoginStartsANewOne() throws Exception {
        Applicant applicant = registered("signout");
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
        assertThat(openSessions(applicant)).isEqualTo(2);

        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        confirmByEmail(applicant.email(), codeFor(applicant.email()), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(openSessions(applicant)).isZero();
        assertThat(allSessions(applicant)).isEqualTo(2);
        login(applicant.username(), NEW_PASSWORD).andExpect(status().isOk());
        assertThat(openSessions(applicant)).isEqualTo(1);
    }

    @Test
    void aRefusedResetLeavesSessionsOpen() throws Exception {
        Applicant applicant = registered("keepsessions");
        login(applicant.username(), OLD_PASSWORD).andExpect(status().isOk());
        requestByEmail(applicant.email()).andExpect(status().isAccepted());

        confirmByEmail(applicant.email(), codeFor(applicant.email()), "short12").andExpect(status().isBadRequest());

        assertThat(openSessions(applicant)).isEqualTo(1);
    }

    private int openSessions(Applicant applicant) {
        return jdbc.queryForObject("""
                select count(*) from sessions s join users u on u.id = s.user_id
                where u.username = ? and s.revoked_at is null""", Integer.class, applicant.username());
    }

    private int allSessions(Applicant applicant) {
        return jdbc.queryForObject("""
                select count(*) from sessions s join users u on u.id = s.user_id
                where u.username = ?""", Integer.class, applicant.username());
    }

    private void resetTo(Applicant applicant, String password) throws Exception {
        confirmByEmail(applicant.email(), requestNewCode(applicant), password).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
    }

    private String requestNewCode(Applicant applicant) throws Exception {
        clock.advance(Duration.ofSeconds(61));
        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        return codeFor(applicant.email());
    }

    private int historyEntries(Applicant applicant) {
        return jdbc.queryForObject("""
                select count(*) from password_history h join users u on u.id = h.user_id
                where u.username = ?""", Integer.class, applicant.username());
    }

    // --- one account's code does not open another ---------------------------------------------------------------

    @Test
    void aCodeSentToOneAccountIsRefusedForAnotherAccountsContact() throws Exception {
        Applicant a = registered("owner-a");
        Applicant b = registered("owner-b");
        requestByEmail(a.email()).andExpect(status().isAccepted());
        String codeOfA = codeFor(a.email());

        confirmByEmail(b.email(), codeOfA, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid or expired code"));

        login(b.username(), OLD_PASSWORD).andExpect(status().isOk());
        assertThat(failedAttempts(a)).isZero();
        confirmByEmail(a.email(), codeOfA, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    // --- nothing secret reaches the logs (SC-007) ------------------------------------------------------------------

    @Test
    void logsNeverContainACodeOrANewPassword(CapturedOutput output) throws Exception {
        String distinctivePassword = "Pw" + UUID.randomUUID().toString().replace("-", "");
        Applicant applicant = registered("logsafe");

        requestByEmail(applicant.email()).andExpect(status().isAccepted());
        String code = codeFor(applicant.email());
        String wrong = "000000".equals(code) ? "000001" : "000000";
        confirmByEmail(applicant.email(), wrong, distinctivePassword).andExpect(status().isBadRequest());
        confirmByEmail(applicant.email(), code, "short12").andExpect(status().isBadRequest());
        confirmByEmail("nobody." + System.nanoTime() + "@example.com", code, distinctivePassword)
                .andExpect(status().isBadRequest());
        confirmByEmail(applicant.email(), code, distinctivePassword).andExpect(status().isNoContent());

        assertThat(output.getAll())
                .doesNotContain(distinctivePassword)
                .doesNotContain("short12")
                .doesNotContain("code=" + code)
                .doesNotContain(": " + code);
    }

    // --- helpers --------------------------------------------------------------------------------------------

    private int failedAttempts(Applicant applicant) {
        return jdbc.queryForObject("""
                select c.failed_attempts from password_reset_codes c join users u on u.id = c.user_id
                where u.username = ? order by c.issued_at desc limit 1""", Integer.class, applicant.username());
    }

    private int issuedCodes(Applicant applicant) {
        return jdbc.queryForObject("""
                select count(*) from password_reset_codes c join users u on u.id = c.user_id
                where u.username = ?""", Integer.class, applicant.username());
    }

    private int usedCodes(Applicant applicant) {
        return jdbc.queryForObject("""
                select count(*) from password_reset_codes c join users u on u.id = c.user_id
                where u.username = ? and c.used_at is not null""", Integer.class, applicant.username());
    }

    private Applicant registered(String prefix) throws Exception {
        Applicant applicant = RegistrationFlowIT.uniqueApplicant(prefix);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(applicant.json()))
                .andExpect(status().isCreated());
        return applicant;
    }

    private String codeFor(String address) {
        return ((RecordingResetCodeSender) sender).latestCodeFor(address);
    }

    private ResultActions requestByEmail(String email) throws Exception {
        return json("/auth/password-reset/request", "{\"email\": \"%s\"}".formatted(email));
    }

    private ResultActions requestByPhone(String phone) throws Exception {
        return json("/auth/password-reset/request", "{\"phone\": \"%s\"}".formatted(phone));
    }

    private ResultActions confirmByEmail(String email, String code, String newPassword) throws Exception {
        return json("/auth/password-reset/confirm",
                "{\"email\": \"%s\", \"code\": \"%s\", \"newPassword\": \"%s\"}".formatted(email, code, newPassword));
    }

    private ResultActions confirmByPhone(String phone, String code, String newPassword) throws Exception {
        return json("/auth/password-reset/confirm",
                "{\"phone\": \"%s\", \"code\": \"%s\", \"newPassword\": \"%s\"}".formatted(phone, code, newPassword));
    }

    private ResultActions login(String username, String password) throws Exception {
        return json("/auth/login", "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password));
    }

    private ResultActions json(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
