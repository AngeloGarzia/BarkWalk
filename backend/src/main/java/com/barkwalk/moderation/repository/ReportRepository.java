package com.barkwalk.moderation.repository;

import com.barkwalk.moderation.domain.Report;
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
// Accès signalements.

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    long countByTargetTypeAndTargetIdAndDecision(String targetType, Long targetId, String decision);
}
