package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Accès conversations.
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByWalkIdAndConversationType(Long walkId, String conversationType);

    List<Conversation> findByConversationTypeAndArchivedAtIsNull(String conversationType);

    @Query(
            """
            select c from Conversation c
            where c.conversationType = 'DIRECT'
              and c.archivedAt is null
              and exists (
                select 1 from ConversationMember m1
                where m1.conversationId = c.id and m1.userId = :userA
              )
              and exists (
                select 1 from ConversationMember m2
                where m2.conversationId = c.id and m2.userId = :userB
              )
              and (
                select count(m) from ConversationMember m where m.conversationId = c.id
              ) = 2
            """)
    List<Conversation> findDirectBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
