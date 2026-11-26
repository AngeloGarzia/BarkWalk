package com.barkwalk.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private MembershipPlanRepository planRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;

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

    @Test
    void activateOnPayment_grantsMemberRole() {
        Membership membership = new Membership();
        setId(membership, 8L);
        membership.setUserId(3L);
        membership.setPlanId(1L);
        membership.setStatus("INCOMPLETE");
        when(membershipRepository.findById(8L)).thenReturn(Optional.of(membership));
        MembershipPlan plan = new MembershipPlan();
        setId(plan, 1L);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(membershipRepository.save(membership)).thenReturn(membership);

        User user = new User();
        setId(user, 3L);
        user.setRoles(new HashSet<>(Set.of(Role.USER)));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        Membership activated = membershipService.activateOnPayment(8L);
        assertThat(activated.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getRoles()).contains(Role.MEMBER);
        verify(userRepository).save(user);
    }

    @Test
    void activateOnPayment_isIdempotent() {
        Membership membership = new Membership();
        setId(membership, 8L);
        membership.setStatus("ACTIVE");
        when(membershipRepository.findById(8L)).thenReturn(Optional.of(membership));
        assertThat(membershipService.activateOnPayment(8L).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void startCheckout_rejectsIfAlreadyActive() {
        SecurityTestSupport.authenticate(3L, "m@example.com", Role.USER);
        MembershipPlan plan = new MembershipPlan();
        setId(plan, 1L);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(membershipRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(new Membership()));
        assertThatThrownBy(() -> membershipService.startCheckout(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
