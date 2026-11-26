package com.barkwalk.walk.dto;

import java.util.List;

public final class WalkParticipantDtos {
    private WalkParticipantDtos() {}

    public record ParticipantResponse(
            Long bookingId,
            Long userId,
            Long dogId,
            String displayName,
            String dogName,
            String bookingStatus,
            String phone) {}

    public record ParticipantsResponse(Long walkId, List<ParticipantResponse> participants) {}
}
