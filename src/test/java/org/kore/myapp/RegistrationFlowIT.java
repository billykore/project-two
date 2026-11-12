package org.kore.myapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration end to end against the real application, PostgreSQL, and the real security configuration.
 * <p>
 * The database keeps committed rows across tests, so every test creates its own applicants with
 * {@link #uniqueApplicant(String)} and never relies on another test's data or on run order.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistrationFlowIT {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** A registration request's values, all unique to one call of {@link #uniqueApplicant(String)}. */
    record Applicant(String username, String password, String name, String email, String phone) {

        Applicant withUsername(String value) {
            return new Applicant(value, password, name, email, phone);
        }

        Applicant withPassword(String value) {
            return new Applicant(username, value, name, email, phone);
        }

        Applicant withEmail(String value) {
            return new Applicant(username, password, name, value, phone);
        }

        Applicant withPhone(String value) {
            return new Applicant(username, password, name, email, value);
        }

        String json() {
            return """
                    {"username": "%s", "password": "%s", "name": "%s", "email": "%s", "phone": "%s"}"""
                    .formatted(username, password, name, email, phone);
        }
    }

    static Applicant uniqueApplicant(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = prefix + "." + suffix;
        long digits = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        return new Applicant(username, PASSWORD, "Name " + prefix, username + "@example.com", "+62" + digits);
    }

    @Test
    void registeredAccountCanLogInAndIsStoredWithABcryptHash() throws Exception {
        Applicant applicant = uniqueApplicant("flow");

        register(applicant)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(applicant.username()))
                .andExpect(jsonPath("$.email").value(applicant.email()))
                .andExpect(jsonPath("$.phone").value(applicant.phone()))
                .andExpect(jsonPath("$.password").doesNotExist());
        login(applicant.username(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        String stored = jdbc.queryForObject(
                "select password from users where username = ?", String.class, applicant.username());
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void loginWithAWrongPasswordIsRejected() throws Exception {
        Applicant applicant = uniqueApplicant("wrongpw");
        register(applicant).andExpect(status().isCreated());

        login(applicant.username(), "not-the-password").andExpect(status().isUnauthorized());
    }

    @Test
    void loginStaysUsernameOnlyEmailAndPhoneAreNotAccepted() throws Exception {
        Applicant applicant = uniqueApplicant("loginid");
        register(applicant).andExpect(status().isCreated());

        login(applicant.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(applicant.phone(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationNeverMakesTheAccountADriver() throws Exception {
        Applicant applicant = uniqueApplicant("customer");

        register(applicant.json().replace("}", ", \"role\": \"DRIVER\"}"))
                .andExpect(status().isCreated());

        Integer drivers = jdbc.queryForObject("""
                select count(*) from driver_profiles d join users u on u.id = d.user_id
                where u.username = ?""", Integer.class, applicant.username());
        assertThat(drivers).isZero();
    }

    @Test
    void reusingAUsernameEmailOrPhoneIsAConflictAndLeavesTheAccountUntouched() throws Exception {
        Applicant a = uniqueApplicant("owner");
        register(a).andExpect(status().isCreated());

        register(uniqueApplicant("dupname").withUsername(a.username()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("username already taken"))
                .andExpect(jsonPath("$.conflicts[0]").value("username"));
        register(uniqueApplicant("dupmail").withEmail(a.email().toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("email already registered"))
                .andExpect(jsonPath("$.conflicts[0]").value("email"));
        register(uniqueApplicant("dupphone").withPhone(a.phone()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("phone number already registered"))
                .andExpect(jsonPath("$.conflicts[0]").value("phone"));
        register(a)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts.length()").value(3));

        login(a.username(), PASSWORD).andExpect(status().isOk());
        var stored = jdbc.queryForMap("select name, email, phone from users where username = ?", a.username());
        assertThat(stored).containsEntry("name", a.name()).containsEntry("email", a.email())
                .containsEntry("phone", a.phone());
    }

    @Test
    void twoSimultaneousRegistrationsOfOneApplicantCreateExactlyOneAccount() throws Exception {
        Applicant applicant = uniqueApplicant("race");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    statuses.add(register(applicant).andReturn().getResponse().getStatus());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        Integer accounts = jdbc.queryForObject(
                "select count(*) from users where username = ?", Integer.class, applicant.username());
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    void invalidRegistrationsAreRejectedAndCreateNothing() throws Exception {
        Applicant shortPassword = uniqueApplicant("shortpw");
        Applicant badUsername = uniqueApplicant("badname").withUsername("Bad Name!");
        Applicant everythingWrong = new Applicant("X", "short", " ", "nope", "0812");

        register(new Applicant(shortPassword.username(), "short12", shortPassword.name(), shortPassword.email(),
                shortPassword.phone()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        register(badUsername)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
        register(everythingWrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));

        assertThat(accountsFor(shortPassword)).isZero();
        assertThat(accountsFor(badUsername)).isZero();
        assertThat(accountsFor(everythingWrong)).isZero();
    }

    /** SC-004: whatever the outcome, the application log never contains the password, email, or phone. */
    @Test
    void logsNeverContainThePasswordEmailOrPhone(CapturedOutput output) throws Exception {
        String distinctivePassword = "Pw" + UUID.randomUUID().toString().replace("-", "");
        Applicant created = uniqueApplicant("logok").withPassword(distinctivePassword);
        Applicant conflicting = uniqueApplicant("logdup").withUsername(created.username())
                .withPassword(distinctivePassword);
        Applicant invalid = uniqueApplicant("loginv").withPassword("short");

        register(created).andExpect(status().isCreated());
        register(conflicting).andExpect(status().isConflict());
        register(invalid).andExpect(status().isBadRequest());

        for (Applicant applicant : List.of(created, conflicting, invalid)) {
            assertThat(output.getAll()).doesNotContain(applicant.password());
            assertThat(output.getAll()).doesNotContain(applicant.email());
            assertThat(output.getAll()).doesNotContain(applicant.phone());
        }
    }

    /** Counts accounts holding any of the applicant's values, not the table total that other tests change. */
    private int accountsFor(Applicant applicant) {
        return jdbc.queryForObject("select count(*) from users where username = ? or email = ? or phone = ?",
                Integer.class, applicant.username(), applicant.email(), applicant.phone());
    }

    private ResultActions register(Applicant applicant) throws Exception {
        return register(applicant.json());
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }
}
