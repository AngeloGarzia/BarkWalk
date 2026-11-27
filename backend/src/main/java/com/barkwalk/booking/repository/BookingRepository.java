package com.barkwalk.booking.repository;

import com.barkwalk.booking.domain.Booking;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
// Comptages et existence de réservations actives par balade ou utilisateur.

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByWalkIdAndStatusIn(Long walkId, Collection<String> statuses);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByWalkId(Long walkId);

    List<Booking> findByDogId(Long dogId);

    boolean existsByWalkIdAndDogIdAndStatusNotIn(Long walkId, Long dogId, Collection<String> statuses);

    boolean existsByUserIdAndTrialTrue(Long userId);

    List<Booking> findByStatusAndPaymentExpiresAtBefore(String status, Instant before);
}
