package com.barkwalk.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Stub d'envoi d'emails (R5 / R7) — journalise tant qu'aucun provider n'est branché.
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void notifyWalkCancelledByStaff(Long walkId, Long userId, String reason) {
        log.info("EMAIL stub: walk {} cancelled for user {} reason={}", walkId, userId, reason);
    }

    public void notifyWaitlistOffer(Long walkId, Long userId, Long entryId) {
        log.info("EMAIL stub: waitlist offer walk={} user={} entry={}", walkId, userId, entryId);
    }
}
