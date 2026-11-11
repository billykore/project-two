package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSessionRepository extends JpaRepository<Session, Long> {
}
