package com.barkwalk.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.moderation.domain.Report;
import com.barkwalk.moderation.dto.ModerationDtos.CreateReportRequest;
import com.barkwalk.moderation.dto.ModerationDtos.DecideRequest;
import com.barkwalk.moderation.dto.ModerationDtos.ReportResponse;
import com.barkwalk.moderation.repository.ReportRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.service.CommunityOrgService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private AuditService auditService;
    @Mock private CommunityOrgService communityOrgService;

    @InjectMocks
    private ModerationService moderationService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(10L, "admin@example.com", Role.USER, Role.ADMIN);
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    /** Crée un signalement avec le statut OPEN et l'identifiant du rapporteur courant. */
    @Test
    void create_savesReportWithOpenStatus() {
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse response = moderationService.create(
                new CreateReportRequest("USER", 5L, "comportement inapproprié"));

        assertThat(response.reporterId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.targetType()).isEqualTo("USER");
        assertThat(response.targetId()).isEqualTo(5L);
    }

    /** Délègue la liste des signalements ouverts au dépôt. */
    @Test
    void openReports_delegatesToRepository() {
        Report report = report(1L, 10L, "USER", 5L, "OPEN");
        when(reportRepository.findByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(report));

        List<ReportResponse> result = moderationService.openReports();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("OPEN");
    }

    /** Ferme le signalement, enregistre la décision et crée une entrée d'audit. */
    @Test
    void decide_closesReportAndAudits() {
        Report report = report(1L, 9L, "WALK", 20L, "OPEN");
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportResponse response = moderationService.decide(1L, new DecideRequest("REJECTED"));

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.decision()).isEqualTo("REJECTED");
        assertThat(response.decidedBy()).isEqualTo(10L);
        verify(auditService).log(10L, "REPORT_DECIDE", "REPORT", 1L, "REJECTED");
    }

    /** Déclenche l'évaluation de révocation si la décision cible un utilisateur et est VALIDATED. */
    @Test
    void decide_triggersRevocationOnUserTargetValidated() {
        Report report = report(2L, 9L, "USER", 7L, "OPEN");
        when(reportRepository.findById(2L)).thenReturn(Optional.of(report));

        moderationService.decide(2L, new DecideRequest("VALIDATED"));

        verify(communityOrgService).evaluateRevocation(7L);
    }

    /** N'appelle pas evaluateRevocation si la cible n'est pas un utilisateur. */
    @Test
    void decide_skipsRevocationForNonUserTarget() {
        Report report = report(3L, 9L, "WALK", 15L, "OPEN");
        when(reportRepository.findById(3L)).thenReturn(Optional.of(report));

        moderationService.decide(3L, new DecideRequest("VALIDATED"));

        verify(communityOrgService, never()).evaluateRevocation(any());
    }

    /** Lève NOT_FOUND si le signalement est introuvable. */
    @Test
    void decide_throwsNotFoundForMissingReport() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderationService.decide(99L, new DecideRequest("REJECTED")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Report report(long id, long reporterId, String targetType, long targetId, String status) {
        Report report = new Report();
        try {
            var f = Report.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(report, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason("test reason");
        report.setStatus(status);
        return report;
    }
}
