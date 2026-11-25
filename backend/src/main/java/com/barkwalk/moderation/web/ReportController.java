package com.barkwalk.moderation.web;

import com.barkwalk.moderation.dto.ModerationDtos.CreateReportRequest;
import com.barkwalk.moderation.dto.ModerationDtos.DecideRequest;
import com.barkwalk.moderation.dto.ModerationDtos.ReportResponse;
import com.barkwalk.moderation.service.ModerationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ModerationService moderationService;

    public ReportController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping
    public ReportResponse create(@Valid @RequestBody CreateReportRequest body) {
        return moderationService.create(body);
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReportResponse> open() {
        return moderationService.openReports();
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('ADMIN')")
    public ReportResponse decide(@PathVariable Long id, @Valid @RequestBody DecideRequest body) {
        return moderationService.decide(id, body);
    }
}
