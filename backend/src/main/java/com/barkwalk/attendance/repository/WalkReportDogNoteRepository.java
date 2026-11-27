package com.barkwalk.attendance.repository;

import com.barkwalk.attendance.domain.WalkReportDogNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Notes chiens d'un compte-rendu.

public interface WalkReportDogNoteRepository extends JpaRepository<WalkReportDogNote, Long> {
    List<WalkReportDogNote> findByReportId(Long reportId);
}
