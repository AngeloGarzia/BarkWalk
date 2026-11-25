package com.barkwalk.attendance.repository;

import com.barkwalk.attendance.domain.WalkReport;
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
// Compte-rendus par balade.

public interface WalkReportRepository extends JpaRepository<WalkReport, Long> {
    Optional<WalkReport> findByWalkId(Long walkId);
}
