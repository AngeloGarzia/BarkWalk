package com.barkwalk.booking.dto;

import java.time.Instant;

public final class BookingDtos {
    private BookingDtos() {}

    public record BookingRequest(Long walkId, Long dogId, boolean communityAck, boolean trial) {}

    public record BookingResponse(
            Long id,
            Long walkId,
            Long dogId,
            String status,
            int priceCents,
            boolean paymentRequired,
            Instant confirmedAt,
            Instant paymentExpiresAt,
            boolean trial,
            boolean communityAck,
            String walkTitle,
            String walkType,
            Instant startsAt,
            String cityName,
            String dogName,
            String organizerDisplayName,
            String participantDisplayName) {}

    public record WaitlistEntryResponse(
            Long id,
            Long walkId,
            Long dogId,
            int position,
            String status,
            Instant offeredAt,
            Instant offerExpiresAt,
            String walkTitle,
            String walkType,
            Instant startsAt,
            String dogName,
            String organizerDisplayName) {}
}
