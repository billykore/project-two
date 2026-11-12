package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kore.myapp.core.exception.InvalidRegistrationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserCommandTest {

    @Test
    void validCommandKeepsUsernamePasswordAndPhoneAsGiven() {
        RegisterUserCommand command = new RegisterUserCommand(
                "budi", "secret 123", "Budi Santoso", "budi@example.com", "+6281234567890");

        assertThat(command.username()).isEqualTo("budi");
        assertThat(command.password()).isEqualTo("secret 123");
        assertThat(command.phone()).isEqualTo("+6281234567890");
    }

    @Test
    void nameIsStripped() {
        RegisterUserCommand command = new RegisterUserCommand(
                "budi", "secret123", "  Budi Santoso ", "budi@example.com", "+6281234567890");

        assertThat(command.name()).isEqualTo("Budi Santoso");
    }

    @Test
    void emailIsStrippedAndLowercased() {
        RegisterUserCommand command = new RegisterUserCommand(
                "budi", "secret123", "Budi", " Budi@Example.com ", "+6281234567890");

        assertThat(command.email()).isEqualTo("budi@example.com");
    }

    @Test
    void toStringMasksThePassword() {
        RegisterUserCommand command = new RegisterUserCommand(
                "budi", "secret123", "Budi", "budi@example.com", "+6281234567890");

        assertThat(command.toString()).contains("password=***").doesNotContain("secret123");
    }

    // --- username -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a.b_c-d9", "budi"})
    void usernameAcceptsAllowedCharactersAtTheShortestLength(String username) {
        assertValid(username, "secret123", "Budi", "budi@example.com", "+6281234567890");
    }

    @Test
    void usernameAcceptsFiftyCharactersAndRejectsFiftyOne() {
        assertValid("a".repeat(50), "secret123", "Budi", "budi@example.com", "+6281234567890");
        assertInvalid(RegistrationField.USERNAME, "a".repeat(51), "secret123", "Budi", "budi@example.com",
                "+6281234567890");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Budi", "bu di", "budi!", " budi", "budi ", "bu@di", "büdi"})
    void usernameRejectsShortOrDisallowedCharacters(String username) {
        assertInvalid(RegistrationField.USERNAME, username, "secret123", "Budi", "budi@example.com",
                "+6281234567890");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void usernameIsRequired(String username) {
        assertThat(errorsOf(username, "secret123", "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.USERNAME, "is required"));
    }

    // --- password -------------------------------------------------------------------------------------------

    @Test
    void passwordAcceptsEightCharactersAndRejectsSeven() {
        assertValid("budi", "12345678", "Budi", "budi@example.com", "+6281234567890");
        assertThat(errorsOf("budi", "1234567", "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.PASSWORD, "must be at least 8 characters"));
    }

    @Test
    void passwordAcceptsSeventyTwoAsciiCharactersAndRejectsSeventyThree() {
        assertValid("budi", "a".repeat(72), "Budi", "budi@example.com", "+6281234567890");
        assertThat(errorsOf("budi", "a".repeat(73), "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.PASSWORD, "must be at most 72 characters"));
    }

    @Test
    void passwordOfFortyTwoByteCharactersIsOverSeventyTwoBytes() {
        String eighty = "é".repeat(40);

        assertThat(errorsOf("budi", eighty, "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.PASSWORD,
                        "must be at most 72 bytes; non-ASCII characters count as more than one"));
        assertValid("budi", "é".repeat(36), "Budi", "budi@example.com", "+6281234567890");
    }

    @Test
    void passwordCountsAnEmojiAsOneCharacterButFourBytes() {
        assertValid("budi", "😀".repeat(18), "Budi", "budi@example.com", "+6281234567890");
        assertThat(errorsOf("budi", "😀".repeat(19), "Budi", "budi@example.com", "+6281234567890"))
                .extracting(FieldError::field).containsExactly(RegistrationField.PASSWORD);
    }

    @Test
    void passwordKeepsItsSpacesAndNeedsNoDigitsCapitalsOrSymbols() {
        assertValid("budi", "abcdefgh", "Budi", "budi@example.com", "+6281234567890");
        assertValid("budi", "  spaced  ", "Budi", "budi@example.com", "+6281234567890");
        assertThat(new RegisterUserCommand("budi", "  spaced  ", "Budi", "budi@example.com", "+6281234567890")
                .password()).isEqualTo("  spaced  ");
    }

    @Test
    void passwordMustDifferFromTheUsername() {
        assertThat(errorsOf("budisantoso", "budisantoso", "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.PASSWORD, "must not be the same as the username"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void passwordIsRequired(String password) {
        assertThat(errorsOf("budi", password, "Budi", "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.PASSWORD, "is required"));
    }

    // --- name -----------------------------------------------------------------------------------------------

    @Test
    void nameAcceptsOneHundredCharactersAndRejectsOneHundredOneAfterStripping() {
        assertValid("budi", "secret123", " " + "n".repeat(100) + " ", "budi@example.com", "+6281234567890");
        assertInvalid(RegistrationField.NAME, "budi", "secret123", "n".repeat(101), "budi@example.com",
                "+6281234567890");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Siti Nurhaliza", "José", "王伟"})
    void nameAcceptsNonAsciiCharacters(String name) {
        assertValid("budi", "secret123", name, "budi@example.com", "+6281234567890");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nameIsRequired(String name) {
        assertThat(errorsOf("budi", "secret123", name, "budi@example.com", "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.NAME, "is required"));
    }

    // --- email ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"budi@example.com", "a.b+c@sub.example.co.id", "x@y.io"})
    void emailAcceptsOrdinaryAddresses(String email) {
        assertValid("budi", "secret123", "Budi", email, "+6281234567890");
    }

    @ParameterizedTest
    @ValueSource(strings = {"budi@", "budi.example.com", "a@b", "a@@b.com", "a b@example.com", "@example.com",
            "budi@-example.com", "budi@example..com", "budi@example.com."})
    void emailRejectsMalformedAddresses(String email) {
        assertThat(errorsOf("budi", "secret123", "Budi", email, "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.EMAIL, "must be a valid email address"));
    }

    @Test
    void emailAcceptsTwoHundredFiftyFourCharactersAndRejectsTwoHundredFiftyFive() {
        String local = "a".repeat(64);
        String label63 = "a".repeat(63);
        assertValid("budi", "secret123", "Budi", local + "@" + label63 + "." + label63 + "." + "a".repeat(61),
                "+6281234567890");
        assertInvalid(RegistrationField.EMAIL, "budi", "secret123", "Budi",
                local + "@" + label63 + "." + label63 + "." + "a".repeat(62), "+6281234567890");
    }

    @Test
    void emailRejectsALocalPartOverSixtyFourCharacters() {
        assertInvalid(RegistrationField.EMAIL, "budi", "secret123", "Budi", "a".repeat(65) + "@example.com",
                "+6281234567890");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void emailIsRequired(String email) {
        assertThat(errorsOf("budi", "secret123", "Budi", email, "+6281234567890"))
                .containsExactly(new FieldError(RegistrationField.EMAIL, "is required"));
    }

    // --- phone ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"+12345678", "+6281234567890", "+123456789012345"})
    void phoneAcceptsEightToFifteenDigitsAfterAPlus(String phone) {
        assertValid("budi", "secret123", "Budi", "budi@example.com", phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"081234567890", "+62 812-3456-7890", "+1234567", "+1234567890123456", "6281234567890",
            "+62(812)345678", "+62abc45678", " +6281234567890"})
    void phoneRejectsAnythingButInternationalForm(String phone) {
        assertThat(errorsOf("budi", "secret123", "Budi", "budi@example.com", phone))
                .containsExactly(new FieldError(RegistrationField.PHONE,
                        "must be in international format, e.g. +6281234567890"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void phoneIsRequired(String phone) {
        assertThat(errorsOf("budi", "secret123", "Budi", "budi@example.com", phone))
                .containsExactly(new FieldError(RegistrationField.PHONE, "is required"));
    }

    // --- several fields at once -----------------------------------------------------------------------------

    @Test
    void severalInvalidFieldsAreListedOnceEachInFieldOrder() {
        List<FieldError> errors = errorsOf("Bad Name!", "short", null, "budi@", "0812");

        assertThat(errors).extracting(FieldError::field).containsExactly(
                RegistrationField.USERNAME, RegistrationField.PASSWORD, RegistrationField.NAME,
                RegistrationField.EMAIL, RegistrationField.PHONE);
    }

    @Test
    void anEntirelyMissingRegistrationReportsEveryFieldAsRequired() {
        assertThat(errorsOf(null, null, null, null, null))
                .containsExactly(
                        new FieldError(RegistrationField.USERNAME, "is required"),
                        new FieldError(RegistrationField.PASSWORD, "is required"),
                        new FieldError(RegistrationField.NAME, "is required"),
                        new FieldError(RegistrationField.EMAIL, "is required"),
                        new FieldError(RegistrationField.PHONE, "is required"));
    }

    @Test
    void aValidCommandThrowsNothing() {
        assertThatCode(() -> new RegisterUserCommand(
                "budi", "secret123", "Budi Santoso", "budi@example.com", "+6281234567890"))
                .doesNotThrowAnyException();
    }

    // --- helpers --------------------------------------------------------------------------------------------

    private static void assertValid(String username, String password, String name, String email, String phone) {
        assertThatCode(() -> new RegisterUserCommand(username, password, name, email, phone))
                .doesNotThrowAnyException();
    }

    private static void assertInvalid(RegistrationField field, String username, String password, String name,
                                      String email, String phone) {
        assertThat(errorsOf(username, password, name, email, phone))
                .extracting(FieldError::field).containsExactly(field);
    }

    private static List<FieldError> errorsOf(String username, String password, String name, String email,
                                             String phone) {
        InvalidRegistrationException[] thrown = new InvalidRegistrationException[1];
        assertThatThrownBy(() -> new RegisterUserCommand(username, password, name, email, phone))
                .isInstanceOfSatisfying(InvalidRegistrationException.class, e -> thrown[0] = e);
        return thrown[0].getErrors();
    }
}
