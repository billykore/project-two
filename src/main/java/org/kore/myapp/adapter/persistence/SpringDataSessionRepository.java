package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SpringDataSessionRepository extends JpaRepository<Session, Long> {

    @Modifying
    @Query("update Session s set s.revokedAt = :at where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") long userId, @Param("at") Instant at);
}
