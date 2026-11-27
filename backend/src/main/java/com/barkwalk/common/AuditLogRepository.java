package com.barkwalk.common;

import org.springframework.data.jpa.repository.JpaRepository;
// Composant du module common. Utilitaires transverses.

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
