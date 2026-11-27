package com.barkwalk.moderation.repository;

import com.barkwalk.moderation.domain.Report;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès signalements.

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    long countByTargetTypeAndTargetIdAndDecision(String targetType, Long targetId, String decision);
}
