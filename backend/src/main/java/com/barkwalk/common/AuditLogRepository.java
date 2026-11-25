package com.barkwalk.common;

import com.barkwalk.common.AuditLog;
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
// Composant du module common. Utilitaires transverses.

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
