package org.kore.myapp.core.entity;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.valueobject.RegisterUserCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private final RegisterUserCommand command = new RegisterUserCommand(
            "budi", "secret123", " Budi Santoso ", "Budi@Example.com", "+6281234567890");

    @Test
    void registerCopiesTheNormalizedDetailsAndStoresTheHash() {
        User user = User.register(command, "bcrypt-hash");

        assertThat(user.getUsername()).isEqualTo("budi");
        assertThat(user.getName()).isEqualTo("Budi Santoso");
        assertThat(user.getEmail()).isEqualTo("budi@example.com");
        assertThat(user.getPhone()).isEqualTo("+6281234567890");
        assertThat(user.getPassword()).isEqualTo("bcrypt-hash").isNotEqualTo(command.password());
    }

    @Test
    void registerLeavesIdAndCreationTimeToTheDatabase() {
        User user = User.register(command, "bcrypt-hash");

        assertThat(user.getId()).isNull();
        assertThat(user.getCreatedAt()).isNull();
    }

    @Test
    void registerRejectsABlankHash() {
        assertThatThrownBy(() -> User.register(command, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.register(command, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePasswordReplacesTheHashAndKeepsTheRestOfTheAccount() {
        User user = User.register(command, "old-hash");

        user.changePassword("new-hash");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getUsername()).isEqualTo("budi");
        assertThat(user.getEmail()).isEqualTo("budi@example.com");
    }

    @Test
    void changePasswordRejectsABlankHashAndKeepsTheOldOne() {
        User user = User.register(command, "old-hash");

        assertThatThrownBy(() -> user.changePassword(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changePassword(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
    }
}
