package com.barkwalk.membership.web;

import com.barkwalk.membership.dto.MembershipDtos.MembershipResponse;
import com.barkwalk.membership.dto.MembershipDtos.PlanResponse;
import com.barkwalk.membership.service.MembershipService;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return membershipService.activePlans();
    }

    @GetMapping("/me")
    public Optional<MembershipResponse> mine() {
        return membershipService.mine();
    }
}
