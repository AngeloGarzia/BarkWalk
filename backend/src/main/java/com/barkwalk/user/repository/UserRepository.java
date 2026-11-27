package com.barkwalk.user.repository;

import com.barkwalk.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
// Recherche utilisateur par e-mail.

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
        boolean existsByEmailIgnoreCase(String email);
}
