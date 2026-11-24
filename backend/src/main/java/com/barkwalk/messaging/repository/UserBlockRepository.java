package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.UserBlock;
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

public interface UserBlockRepository extends JpaRepository<UserBlock, com.barkwalk.messaging.domain.UserBlock.Pk> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
