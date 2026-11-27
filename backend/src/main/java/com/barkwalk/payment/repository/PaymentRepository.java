package com.barkwalk.payment.repository;

import com.barkwalk.payment.domain.Payment;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès paiements.

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByMembershipId(Long membershipId);
}
