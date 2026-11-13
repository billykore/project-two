package org.kore.myapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kore.myapp.core.valueobject.RegisterUserCommand;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    /** Lowercase; null only for accounts created before registration collected it. */
    @Column(unique = true, length = 254)
    private String email;

    /** International form, such as {@code +6281234567890}; null only for accounts created before registration. */
    @Column(unique = true, length = 16)
    private String phone;

    /** Set by the database when the user row is inserted; no rule or response reads it. */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Builds a new, unsaved customer account.
     *
     * @param command      the validated registration input.
     * @param passwordHash the one-way hash of the command's password.
     * @return a user without an id or creation time; the database assigns both.
     * @throws IllegalArgumentException if {@code passwordHash} is null or blank.
     */
    public static User register(RegisterUserCommand command, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        return User.builder()
                .username(command.username())
                .password(passwordHash)
                .name(command.name())
                .email(command.email())
                .phone(command.phone())
                .build();
    }

    /**
     * Replaces the password.
     *
     * @param newPasswordHash the one-way hash of the new password.
     * @throws IllegalArgumentException if {@code newPasswordHash} is null or blank.
     */
    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        this.password = newPasswordHash;
    }
}
