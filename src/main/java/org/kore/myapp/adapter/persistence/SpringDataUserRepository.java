package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
