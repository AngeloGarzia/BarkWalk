package com.barkwalk.booking.dto;

import java.time.Instant;

public final class BookingDtos {
    private BookingDtos() {}

    public record BookingRequest(Long walkId, Long dogId, boolean communityAck, boolean trial) {}

    public record BookingResponse(
            Long id, Long walkId, Long dogId, String status, int priceCents,
            boolean paymentRequired, Instant confirmedAt) {}
}
