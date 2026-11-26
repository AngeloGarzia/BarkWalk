package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Messages d'une conversation.
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countBySenderIdAndCreatedAtAfter(Long senderId, Instant after);

    @Query(
            """
            select count(m) from Message m
            where m.conversationId = :conversationId
              and (:since is null or m.createdAt > :since)
            """)
    long countUnread(@Param("conversationId") Long conversationId, @Param("since") Instant since);
}
