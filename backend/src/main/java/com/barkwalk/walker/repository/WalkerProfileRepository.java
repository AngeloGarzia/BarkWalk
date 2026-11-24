package com.barkwalk.walker.repository;

import com.barkwalk.walker.domain.WalkerProfile;
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

public interface WalkerProfileRepository extends JpaRepository<WalkerProfile, Long> {
    Optional<WalkerProfile> findByUserId(Long userId);
        List<WalkerProfile> findByStatus(String status);
}
