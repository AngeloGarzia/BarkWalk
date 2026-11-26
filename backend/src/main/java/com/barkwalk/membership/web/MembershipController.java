package com.barkwalk.membership.web;

import com.barkwalk.membership.dto.MembershipDtos.MembershipResponse;
import com.barkwalk.membership.dto.MembershipDtos.PlanResponse;
import com.barkwalk.membership.service.MembershipService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public record CheckoutBody(@NotNull Long planId) {}

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return membershipService.activePlans();
    }

    @GetMapping("/me")
    public Optional<MembershipResponse> mine() {
        return membershipService.mine();
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse checkout(@RequestBody CheckoutBody body) {
        return membershipService.startCheckout(body.planId());
    }

    @PostMapping("/{id}/cancel")
    public MembershipResponse cancel(@PathVariable Long id) {
        return membershipService.cancel(id);
    }
}
