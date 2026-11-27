package com.barkwalk.attendance.repository;

import com.barkwalk.attendance.domain.WalkReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
// Compte-rendus par balade.

public interface WalkReportRepository extends JpaRepository<WalkReport, Long> {
    Optional<WalkReport> findByWalkId(Long walkId);
}
