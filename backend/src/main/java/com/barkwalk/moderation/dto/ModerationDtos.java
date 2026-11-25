package com.barkwalk.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ModerationDtos {
    private ModerationDtos() {}

    public record CreateReportRequest(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String reason) {}

    public record DecideRequest(@NotBlank String decision) {}

    public record ReportResponse(
            Long id,
            Long reporterId,
            String targetType,
            Long targetId,
            String reason,
            String status,
            String decision,
            Long decidedBy,
            Instant decidedAt) {}
}
