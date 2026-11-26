package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.Conversation;
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
// Accès conversations.

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByConversationTypeAndArchivedAtIsNullAndCreatedAtBefore(
            String conversationType, Instant before);
}
