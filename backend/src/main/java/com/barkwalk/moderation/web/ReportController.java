package com.barkwalk.moderation.web;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.moderation.domain.Report;
import com.barkwalk.moderation.repository.ReportRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportRepository reportRepository;
    private final AuditService auditService;

    public ReportController(ReportRepository reportRepository, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    public record CreateReport(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String reason) {}
    public record DecideRequest(@NotBlank String decision) {}

    @PostMapping
    @Transactional
    public Report create(@RequestBody CreateReport body) {
        Report report = new Report();
        report.setReporterId(SecurityUtils.currentUserId());
        report.setTargetType(body.targetType());
        report.setTargetId(body.targetId());
        report.setReason(body.reason());
        report.setStatus("OPEN");
        return reportRepository.save(report);
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Report> open() {
        return reportRepository.findByStatusOrderByCreatedAtDesc("OPEN");
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Report decide(@PathVariable Long id, @RequestBody DecideRequest body) {
        Report report = reportRepository.findById(id).orElseThrow();
        report.setStatus("CLOSED");
        report.setDecision(body.decision());
        report.setDecidedBy(SecurityUtils.currentUserId());
        report.setDecidedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "REPORT_DECIDE", "REPORT", id, body.decision());
        return report;
    }
}
