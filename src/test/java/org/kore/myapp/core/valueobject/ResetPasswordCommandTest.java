package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.exception.InvalidPasswordResetException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetPasswordCommandTest {

    private static final String EITHER = "provide either email or phone";

    @Test
    void validInputKeepsTheCodeAndPasswordExactlyAndNormalizesTheContact() {
        ResetPasswordCommand command = new ResetPasswordCommand(" Budi@Example.com", null, "004213", " pass word ");

        assertThat(command.contact()).isEqualTo(new Contact(ContactChannel.EMAIL, "budi@example.com"));
        assertThat(command.code()).isEqualTo("004213");
        assertThat(command.newPassword()).isEqualTo(" pass word ");
    }

    @Test
    void aPhoneContactIsAccepted() {
        ResetPasswordCommand command = new ResetPasswordCommand(null, "+6281234567890", "123456", "newsecret99");

        assertThat(command.contact()).isEqualTo(new Contact(ContactChannel.PHONE, "+6281234567890"));
    }

    @Test
    void theContactRulesMatchTheRequestCommand() {
        assertThat(errorsOf("a@b.co", "+6281234567890", "123456", "newsecret99")).containsExactly(
                new FieldError("email", EITHER), new FieldError("phone", EITHER));
        assertThat(errorsOf("budi@", null, "123456", "newsecret99")).containsExactly(
                new FieldError("email", "must be a valid email address"));
    }

    @Test
    void aCodeWithLeadingZerosIsValid() {
        assertThatCode(() -> new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99"))
                .doesNotThrowAnyException();
    }

    @Test
    void aCodeThatIsNotExactlySixAsciiDigitsIsRejected() {
        for (String code : new String[]{"12345", "1234567", "12 456", "abc123", "12345a", "", " ", "١٢٣٤٥٦"}) {
            assertThat(errorsOf("budi@example.com", null, code, "newsecret99"))
                    .as("code '%s'", code)
                    .containsExactly(new FieldError("code", "must be 6 digits"));
        }
        assertThat(errorsOf("budi@example.com", null, null, "newsecret99"))
                .containsExactly(new FieldError("code", "must be 6 digits"));
    }

    @Test
    void theNewPasswordFollowsTheRegistrationFormatRules() {
        assertThat(errorsOf("budi@example.com", null, "123456", "short12"))
                .containsExactly(new FieldError("newPassword", "must be at least 8 characters"));
        assertThat(errorsOf("budi@example.com", null, "123456", null))
                .containsExactly(new FieldError("newPassword", "is required"));
        assertThat(errorsOf("budi@example.com", null, "123456", "a".repeat(73)))
                .containsExactly(new FieldError("newPassword", "must be at most 72 characters"));
    }

    @Test
    void everyInvalidFieldIsReportedTogetherInFieldOrder() {
        assertThat(errorsOf(null, "0812", "1", "x")).extracting(FieldError::field)
                .containsExactly("phone", "code", "newPassword");
        assertThat(errorsOf(null, null, "1", "x")).extracting(FieldError::field)
                .containsExactly("email", "phone", "code", "newPassword");
    }

    @Test
    void toStringShowsNeitherTheCodeNorThePassword() {
        String text = new ResetPasswordCommand("budi@example.com", null, "004213", "newsecret99").toString();

        assertThat(text).doesNotContain("004213").doesNotContain("newsecret99").contains("budi@example.com");
    }

    private static List<FieldError> errorsOf(String email, String phone, String code, String newPassword) {
        InvalidPasswordResetException[] holder = new InvalidPasswordResetException[1];
        assertThatThrownBy(() -> new ResetPasswordCommand(email, phone, code, newPassword))
                .isInstanceOfSatisfying(InvalidPasswordResetException.class, e -> holder[0] = e);
        return holder[0].getErrors();
    }
}
