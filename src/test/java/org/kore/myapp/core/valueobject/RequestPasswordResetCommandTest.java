package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.exception.InvalidPasswordResetException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestPasswordResetCommandTest {

    private static final String EITHER = "provide either email or phone";

    @Test
    void anEmailAloneIsAcceptedStrippedAndLowercased() {
        RequestPasswordResetCommand command = new RequestPasswordResetCommand("  Budi@Example.COM ", null);

        assertThat(command.contact()).isEqualTo(new Contact(ContactChannel.EMAIL, "budi@example.com"));
    }

    @Test
    void aPhoneAloneIsAccepted() {
        RequestPasswordResetCommand command = new RequestPasswordResetCommand(null, "+6281234567890");

        assertThat(command.contact()).isEqualTo(new Contact(ContactChannel.PHONE, "+6281234567890"));
    }

    @Test
    void aBlankValueCountsAsAbsent() {
        assertThat(new RequestPasswordResetCommand("   ", "+6281234567890").contact().channel())
                .isEqualTo(ContactChannel.PHONE);
        assertThat(new RequestPasswordResetCommand("budi@example.com", "").contact().channel())
                .isEqualTo(ContactChannel.EMAIL);
    }

    @Test
    void bothGivenReportsBothFields() {
        assertThat(errorsOf("budi@example.com", "+6281234567890")).containsExactly(
                new FieldError("email", EITHER), new FieldError("phone", EITHER));
    }

    @Test
    void neitherGivenReportsBothFields() {
        assertThat(errorsOf(null, null)).containsExactly(
                new FieldError("email", EITHER), new FieldError("phone", EITHER));
        assertThat(errorsOf(" ", "")).containsExactly(
                new FieldError("email", EITHER), new FieldError("phone", EITHER));
    }

    @Test
    void anInvalidEmailIsRejected() {
        assertThat(errorsOf("budi@", null)).containsExactly(
                new FieldError("email", "must be a valid email address"));
        assertThat(errorsOf("budi.example.com", null)).containsExactly(
                new FieldError("email", "must be a valid email address"));
    }

    @Test
    void aPhoneWithoutACountryCodeOrWithSeparatorsIsRejected() {
        String message = "must be in international format, e.g. +6281234567890";

        assertThat(errorsOf(null, "081234567890")).containsExactly(new FieldError("phone", message));
        assertThat(errorsOf(null, "+62 812-3456-7890")).containsExactly(new FieldError("phone", message));
    }

    private static List<FieldError> errorsOf(String email, String phone) {
        return thrownBy(email, phone).getErrors();
    }

    private static InvalidPasswordResetException thrownBy(String email, String phone) {
        InvalidPasswordResetException[] holder = new InvalidPasswordResetException[1];
        assertThatThrownBy(() -> new RequestPasswordResetCommand(email, phone))
                .isInstanceOfSatisfying(InvalidPasswordResetException.class, e -> holder[0] = e);
        return holder[0];
    }
}
