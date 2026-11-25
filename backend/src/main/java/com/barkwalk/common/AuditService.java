package com.barkwalk.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Écriture seule dans le journal d'audit (pas de mise à jour ni suppression).
@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(Long actorId, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        repository.save(log);
    }
}
