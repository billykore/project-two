package org.kore.myapp.adapter.persistence;

import jakarta.persistence.LockModeType;
import org.kore.myapp.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

interface SpringDataUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** Locks the row ({@code FOR NO KEY UPDATE}) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedByEmail(String email);

    /** Locks the row ({@code FOR NO KEY UPDATE}) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedByPhone(String phone);
}
