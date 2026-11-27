package com.barkwalk.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    /** Balade dont l'heure est dépassée et le pointage n'est pas encore validé. */
    public record PendingRollcallWalk(
            Long walkId,
            String title,
            String cityName,
            Instant startsAt,
            int durationMinutes,
            Instant endsAt,
            String status,
            int confirmedCount) {}

    public record RosterEntry(
            Long bookingId,
            Long userId,
            String displayName,
            Long dogId,
            String dogName,
            String bookingStatus,
            String attendanceStatus) {}

    public record RosterResponse(
            Long walkId,
            String title,
            Instant startsAt,
            Instant endsAt,
            Instant attendanceValidatedAt,
            List<RosterEntry> entries) {}

    public record RollcallItem(@NotNull Long bookingId, @NotBlank String status) {}

    public record RollcallRequest(@NotEmpty List<@Valid RollcallItem> items) {}

    public record RollcallResponse(
            Long walkId, Instant attendanceValidatedAt, int presentCount, int absentCount) {}
}
