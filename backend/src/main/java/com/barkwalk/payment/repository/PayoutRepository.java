package com.barkwalk.payment.repository;

import com.barkwalk.payment.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès versements.

public interface PayoutRepository extends JpaRepository<Payout, Long> {
}
