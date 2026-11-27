package com.barkwalk.booking.repository;

import com.barkwalk.booking.domain.WaitlistEntry;
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
// Gestion de la file d'attente par balade.

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByWalkIdAndStatusOrderByPositionAsc(Long walkId, String status);

    long countByWalkId(Long walkId);

    boolean existsByWalkIdAndDogIdAndStatusIn(Long walkId, Long dogId, Collection<String> statuses);

    List<WaitlistEntry> findByStatusAndOfferExpiresAtBefore(String status, Instant before);

    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WaitlistEntry> findByWalkId(Long walkId);

    void deleteByWalkId(Long walkId);
}
