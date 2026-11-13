package org.kore.myapp.config;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.port.ResetCodeGenerator;
import org.kore.myapp.core.valueobject.PasswordResetPolicy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    private final ApplicationConfig config = new ApplicationConfig();

    @Test
    void generatedCodesAreAlwaysSixDigits() {
        ResetCodeGenerator generator = config.resetCodeGenerator();

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String code = generator.newCode();
            assertThat(code).matches("[0-9]{6}");
            codes.add(code);
        }
        assertThat(codes).hasSizeGreaterThan(900);
    }

    @Test
    void policyIsBuiltFromTheProperties() {
        PasswordResetProperties props = new PasswordResetProperties(
                Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5);

        PasswordResetPolicy policy = config.passwordResetPolicy(props);

        assertThat(policy).isEqualTo(new PasswordResetPolicy(
                Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5));
    }
}
