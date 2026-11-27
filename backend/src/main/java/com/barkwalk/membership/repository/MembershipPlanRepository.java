package com.barkwalk.membership.repository;

import com.barkwalk.membership.domain.MembershipPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Plans actifs.

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    List<MembershipPlan> findByActiveTrue();
}
