package com.barkwalk.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class MessagingDtos {
    private MessagingDtos() {}

    public record CreateConversationRequest(@NotBlank String type, @NotNull List<Long> memberIds, Long walkId) {}

    public record MessageRequest(@NotBlank String body) {}

    public record ConversationResponse(Long id, String conversationType, Long walkId, Instant createdAt, Instant archivedAt) {}

    public record MessageResponse(
            Long id,
            Long conversationId,
            Long senderId,
            String senderDisplayName,
            String body,
            Instant createdAt,
            Instant readAt) {}
}
