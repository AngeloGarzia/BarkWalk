package com.barkwalk.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditService auditService;

    // Persiste une entrée d'audit en base.
    @Test
    void log_persistsEntry() {
        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        auditService.log(1L, "DOG_VALIDATE", "DOG", 5L, null);
        verify(repository).save(any(AuditLog.class));
    }
}
