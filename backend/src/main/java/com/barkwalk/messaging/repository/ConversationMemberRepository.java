package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.ConversationMember;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
// Membres d'une conversation.

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, com.barkwalk.messaging.domain.ConversationMember.Pk> {
    List<ConversationMember> findByUserId(Long userId);
        boolean existsByConversationIdAndUserId(Long conversationId, Long userId);
}
