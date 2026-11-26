package com.barkwalk.membership.repository;

import com.barkwalk.membership.domain.Membership;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
// Adhésions par utilisateur.

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<Membership> findByStatusAndCurrentPeriodEndBefore(String status, Instant before);
}
