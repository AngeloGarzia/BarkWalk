package com.barkwalk.walker.repository;

import com.barkwalk.walker.domain.WalkerProfile;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès profils promeneurs.

public interface WalkerProfileRepository extends JpaRepository<WalkerProfile, Long> {
    Optional<WalkerProfile> findByUserId(Long userId);
        List<WalkerProfile> findByStatus(String status);
}
