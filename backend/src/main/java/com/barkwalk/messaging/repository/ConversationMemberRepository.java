package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.ConversationMember;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Membres d'une conversation.

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, com.barkwalk.messaging.domain.ConversationMember.Pk> {
    List<ConversationMember> findByUserId(Long userId);

    List<ConversationMember> findByConversationId(Long conversationId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
}
