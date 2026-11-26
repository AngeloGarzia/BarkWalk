package com.barkwalk.membership.service;

import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.dto.MembershipDtos.MembershipResponse;
import com.barkwalk.membership.dto.MembershipDtos.PlanResponse;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// R14 : cycle de vie adhésion (ACTIVE / PAST_DUE / CANCELLED / EXPIRED).
@Service
public class MembershipService {
    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public MembershipService(
            MembershipPlanRepository planRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
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

    /** Démarre une adhésion INCOMPLETE en attendant confirmation paiement serveur. */
    @Transactional
    public MembershipResponse startCheckout(Long planId) {
        Long userId = SecurityUtils.currentUserId();
        MembershipPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        if (!plan.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan inactive");
        }
        if (membershipRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE").isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already active member");
        }
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setPlanId(plan.getId());
        membership.setStatus("INCOMPLETE");
        return toMembership(membershipRepository.save(membership));
    }

    /**
     * R14 : droits adhérent actifs dès confirmation du premier paiement.
     * Idempotent si déjà ACTIVE.
     */
    @Transactional
    public Membership activateOnPayment(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        if ("ACTIVE".equals(membership.getStatus())) {
            return membership;
        }
        MembershipPlan plan = planRepository.findById(membership.getPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        Instant now = Instant.now();
        membership.setStatus("ACTIVE");
        membership.setCurrentPeriodStart(now);
        membership.setCurrentPeriodEnd(periodEnd(now, plan.getIntervalUnit()));
        membership.setUpdatedAt(now);
        membership = membershipRepository.save(membership);
        grantMemberRole(membership.getUserId());
        return membership;
    }

    @Transactional
    public MembershipResponse markPastDue(Long membershipId) {
        Membership membership = requireMembership(membershipId);
        membership.setStatus("PAST_DUE");
        membership.setUpdatedAt(Instant.now());
        return toMembership(membershipRepository.save(membership));
    }

    @Transactional
    public MembershipResponse cancel(Long membershipId) {
        Membership membership = requireMembership(membershipId);
        if (!membership.getUserId().equals(SecurityUtils.currentUserId())
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        membership.setStatus("CANCELLED");
        membership.setCancelAtPeriodEnd(false);
        membership.setUpdatedAt(Instant.now());
        return toMembership(membershipRepository.save(membership));
    }

    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now();
        int count = 0;
        for (Membership membership : membershipRepository.findByStatusAndCurrentPeriodEndBefore("ACTIVE", now)) {
            membership.setStatus("EXPIRED");
            membership.setUpdatedAt(now);
            membershipRepository.save(membership);
            count++;
        }
        for (Membership membership : membershipRepository.findByStatusAndCurrentPeriodEndBefore("PAST_DUE", now)) {
            membership.setStatus("EXPIRED");
            membership.setUpdatedAt(now);
            membershipRepository.save(membership);
            count++;
        }
        return count;
    }

    private Membership requireMembership(Long id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void grantMemberRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.getRoles().contains(Role.MEMBER)) {
            user.getRoles().add(Role.MEMBER);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }
    }

    private static Instant periodEnd(Instant start, String intervalUnit) {
        if ("YEAR".equalsIgnoreCase(intervalUnit)) {
            return start.plus(365, ChronoUnit.DAYS);
        }
        return start.plus(30, ChronoUnit.DAYS);
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
