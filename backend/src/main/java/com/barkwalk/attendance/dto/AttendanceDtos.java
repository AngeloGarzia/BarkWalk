package com.barkwalk.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class AttendanceDtos {
    private AttendanceDtos() {}

    public record PointRequest(@NotNull Long bookingId, @NotBlank String status) {}

    public record DogNote(@NotNull Long dogId, Integer behaviorScore, String comment) {}

    public record ReportRequest(
            @NotNull Long walkId,
            Integer actualDurationMinutes,
            String routeDone,
            String incidentType,
            String incidentDescription,
            String incidentSeverity,
            List<DogNote> dogNotes) {}

    public record AttendanceResponse(
            Long id,
            Long bookingId,
            String departureStatus,
            Instant departureAt,
            String returnStatus,
            Instant returnAt) {}

    public record WalkReportResponse(
            Long id, Long walkId, Long authorId, Integer actualDurationMinutes, String routeDone, Instant createdAt) {}
}
