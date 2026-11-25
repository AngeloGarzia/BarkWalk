package com.barkwalk.membership.dto;

import java.time.Instant;

public final class MembershipDtos {
    private MembershipDtos() {}

    public record PlanResponse(
            Long id,
            String code,
            String name,
            String description,
            int priceCents,
            String currency,
            String intervalUnit,
            boolean active) {}

    public record MembershipResponse(
            Long id,
            Long planId,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd) {}
}
