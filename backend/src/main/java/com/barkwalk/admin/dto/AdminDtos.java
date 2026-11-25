package com.barkwalk.admin.dto;

import com.barkwalk.payment.dto.PaymentDtos.PaymentResponse;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record DashboardResponse(
            long users,
            long walks,
            long bookings,
            long memberships,
            int revenueCents,
            int commissionCents) {}

    public record SettingResponse(String key, String value, String description, Instant updatedAt) {}

    public record SettingUpdateRequest(String value) {}

    public record FinanceResponse(List<PaymentResponse> payments) {}
}
