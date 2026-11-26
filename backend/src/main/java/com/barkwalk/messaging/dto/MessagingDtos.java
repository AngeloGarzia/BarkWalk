package com.barkwalk.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class MessagingDtos {
    private MessagingDtos() {}

    public record CreateConversationRequest(@NotBlank String type, @NotNull List<Long> memberIds, Long walkId) {}

    public record OpenDirectRequest(@NotNull Long userId) {}

    public record MessageRequest(@NotBlank String body) {}

    public record MemberSummary(Long userId, String displayName) {}

    public record ConversationResponse(
            Long id,
            String conversationType,
            Long walkId,
            String title,
            String lastMessagePreview,
            Instant lastMessageAt,
            int unreadCount,
            Instant createdAt,
            Instant archivedAt,
            List<MemberSummary> members) {}

    public record MessageResponse(
            Long id,
            Long conversationId,
            Long senderId,
            String senderDisplayName,
            String body,
            Instant createdAt,
            Instant readAt) {}
}
