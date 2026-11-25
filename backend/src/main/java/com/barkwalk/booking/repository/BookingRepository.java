package com.barkwalk.booking.repository;

import com.barkwalk.booking.domain.Booking;
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

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByWalkIdAndStatusIn(Long walkId, Collection<String> statuses);
        List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
        List<Booking> findByWalkId(Long walkId);
        boolean existsByWalkIdAndDogIdAndStatusNotIn(Long walkId, Long dogId, Collection<String> statuses);
}
