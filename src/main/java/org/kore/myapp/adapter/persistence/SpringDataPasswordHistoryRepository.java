package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.PasswordHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataPasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, Long> {

    @Query("select h.passwordHash from PasswordHistoryEntry h where h.userId = :userId "
            + "order by h.replacedAt desc, h.id desc")
    List<String> findRecentHashes(@Param("userId") long userId, Pageable page);

    @Modifying
    @Query(nativeQuery = true, value = """
            delete from password_history
            where user_id = :userId
              and id not in (select id from password_history
                             where user_id = :userId
                             order by replaced_at desc, id desc
                             limit :keep)""")
    void keepNewest(@Param("userId") long userId, @Param("keep") int keep);
}
