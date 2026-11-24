package com.barkwalk.membership.web;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;

    public MembershipController(MembershipPlanRepository planRepository, MembershipRepository membershipRepository) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
    }

    @GetMapping("/plans")
    public List<MembershipPlan> plans() { return planRepository.findByActiveTrue(); }

    @GetMapping("/me")
    public Optional<Membership> mine() {
        return membershipRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                SecurityUtils.currentUserId(), "ACTIVE");
    }
}
