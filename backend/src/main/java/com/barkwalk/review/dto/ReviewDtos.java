package com.barkwalk.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewRequest(
            @NotNull Long walkId,
            @NotNull Long targetUserId,
            @Min(1) @Max(5) int rating,
            String comment) {}

    public record ReviewResponse(
            Long id,
            Long walkId,
            Long authorId,
            String authorDisplayName,
            Long targetUserId,
            String targetDisplayName,
            int rating,
            String comment,
            String reply,
            Instant createdAt) {}

    /** Note d'un participant sur la balade elle-même (1–5). */
    public record WalkRatingRequest(
            @NotNull Long walkId,
            @Min(1) @Max(5) int rating,
            String comment) {}

    public record WalkRatingResponse(
            Long id,
            Long walkId,
            String walkTitle,
            Instant startsAt,
            Long userId,
            int rating,
            String comment,
            Instant createdAt,
            boolean canRate) {}

    public record WalkRatingSummary(
            Long walkId,
            double averageRating,
            long ratingsCount,
            Integer myRating,
            boolean canRate) {}
}
