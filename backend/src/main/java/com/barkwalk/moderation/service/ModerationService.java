package com.barkwalk.moderation.service;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.moderation.domain.Report;
import com.barkwalk.moderation.dto.ModerationDtos.CreateReportRequest;
import com.barkwalk.moderation.dto.ModerationDtos.DecideRequest;
import com.barkwalk.moderation.dto.ModerationDtos.ReportResponse;
import com.barkwalk.moderation.repository.ReportRepository;
import com.barkwalk.user.service.CommunityOrgService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationService {
    private final ReportRepository reportRepository;
    private final AuditService auditService;
    private final CommunityOrgService communityOrgService;

    public ModerationService(
            ReportRepository reportRepository,
            AuditService auditService,
            CommunityOrgService communityOrgService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
        this.communityOrgService = communityOrgService;
    }

    @Transactional
    public ReportResponse create(CreateReportRequest body) {
        Report report = new Report();
        report.setReporterId(SecurityUtils.currentUserId());
        report.setTargetType(body.targetType());
        report.setTargetId(body.targetId());
        report.setReason(body.reason());
        report.setStatus("OPEN");
        return toResponse(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> openReports() {
        return reportRepository.findByStatusOrderByCreatedAtDesc("OPEN").stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReportResponse decide(Long id, DecideRequest body) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        report.setStatus("CLOSED");
        report.setDecision(body.decision());
        report.setDecidedBy(SecurityUtils.currentUserId());
        report.setDecidedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "REPORT_DECIDE", "REPORT", id, body.decision());
        if ("USER".equals(report.getTargetType()) && "VALIDATED".equalsIgnoreCase(body.decision())) {
            communityOrgService.evaluateRevocation(report.getTargetId());
        }
        return toResponse(report);
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getReporterId(),
                r.getTargetType(),
                r.getTargetId(),
                r.getReason(),
                r.getStatus(),
                r.getDecision(),
                r.getDecidedBy(),
                r.getDecidedAt());
    }
}
