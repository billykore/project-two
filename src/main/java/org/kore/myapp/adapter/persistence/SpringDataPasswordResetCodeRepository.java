package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface SpringDataPasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    @Query("select c from PasswordResetCode c where c.userId = :userId and c.usedAt is null "
            + "and c.invalidatedAt is null")
    Optional<PasswordResetCode> findOpen(@Param("userId") long userId);

    long countByUserIdAndIssuedAtGreaterThanEqual(long userId, Instant since);
}
