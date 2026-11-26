package com.barkwalk.walk.dto;

import java.time.Instant;
import java.util.List;

public final class WalkPhotoDtos {
    private WalkPhotoDtos() {}

    public record WalkPhotoResponse(
            Long id,
            Long walkId,
            String kind,
            String url,
            short sortOrder,
            String caption,
            Long uploadedBy,
            String uploaderDisplayName,
            Instant createdAt) {}

    public record WalkHistoryComment(
            Long walkId,
            String walkTitle,
            Instant walkStartsAt,
            Long userId,
            String displayName,
            int rating,
            String comment,
            Instant createdAt) {}

    public record WalkHistoryResponse(
            Long walkId,
            Long recycledFromWalkId,
            List<Long> lineageWalkIds,
            List<WalkPhotoResponse> photos,
            List<WalkHistoryComment> ratingComments) {}
}
