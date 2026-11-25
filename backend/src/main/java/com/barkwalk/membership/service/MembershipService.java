package com.barkwalk.membership.service;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.dto.MembershipDtos.MembershipResponse;
import com.barkwalk.membership.dto.MembershipDtos.PlanResponse;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {
    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;

    public MembershipService(MembershipPlanRepository planRepository, MembershipRepository membershipRepository) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> activePlans() {
        return planRepository.findByActiveTrue().stream().map(this::toPlan).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MembershipResponse> mine() {
        return membershipRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(SecurityUtils.currentUserId(), "ACTIVE")
                .map(this::toMembership);
    }

    private PlanResponse toPlan(MembershipPlan p) {
        return new PlanResponse(
                p.getId(), p.getCode(), p.getName(), p.getDescription(),
                p.getPriceCents(), p.getCurrency(), p.getIntervalUnit(), p.isActive());
    }

    private MembershipResponse toMembership(Membership m) {
        return new MembershipResponse(
                m.getId(), m.getPlanId(), m.getStatus(),
                m.getCurrentPeriodStart(), m.getCurrentPeriodEnd(), m.isCancelAtPeriodEnd());
    }
}
