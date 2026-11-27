package com.barkwalk.attendance.repository;

import com.barkwalk.attendance.domain.Attendance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
// Pointages par réservation.

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByBookingId(Long bookingId);
}
