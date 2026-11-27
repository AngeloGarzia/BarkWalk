package com.barkwalk.messaging.repository;

import com.barkwalk.messaging.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès données du module messaging. Messagerie.

public interface UserBlockRepository extends JpaRepository<UserBlock, com.barkwalk.messaging.domain.UserBlock.Pk> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
