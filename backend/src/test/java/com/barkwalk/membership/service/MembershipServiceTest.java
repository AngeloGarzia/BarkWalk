package com.barkwalk.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.support.SecurityTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private MembershipPlanRepository planRepository;
    @Mock
    private MembershipRepository membershipRepository;

    @InjectMocks
    private MembershipService membershipService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void activePlans_omitsStripeFields() {
        MembershipPlan plan = new MembershipPlan();
        when(planRepository.findByActiveTrue()).thenReturn(List.of(plan));
        assertThat(membershipService.activePlans()).hasSize(1);
    }

    @Test
    void mine_returnsActiveMembership() {
        SecurityTestSupport.authenticate(3L, "m@example.com", Role.USER, Role.MEMBER);
        Membership membership = new Membership();
        membership.setUserId(3L);
        membership.setPlanId(1L);
        membership.setStatus("ACTIVE");
        when(membershipRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(membership));
        assertThat(membershipService.mine()).isPresent();
        assertThat(membershipService.mine().orElseThrow().planId()).isEqualTo(1L);
    }
}
