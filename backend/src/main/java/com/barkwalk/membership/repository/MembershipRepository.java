package com.barkwalk.membership.repository;

import com.barkwalk.membership.domain.Membership;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
// Adhésions par utilisateur.

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Membership> findByStatusAndCurrentPeriodEndBefore(String status, Instant before);
}
