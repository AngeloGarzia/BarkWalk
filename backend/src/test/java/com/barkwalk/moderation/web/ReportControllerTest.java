package com.barkwalk.moderation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.moderation.dto.ModerationDtos.CreateReportRequest;
import com.barkwalk.moderation.dto.ModerationDtos.DecideRequest;
import com.barkwalk.moderation.dto.ModerationDtos.ReportResponse;
import com.barkwalk.moderation.service.ModerationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ModerationService moderationService;

    @InjectMocks
    private ReportController reportController;

    // Enregistre un signalement utilisateur.
    @Test
    void create_delegatesToService() {
        CreateReportRequest body = new CreateReportRequest("USER", 3L, "comportement");
        ReportResponse response = new ReportResponse(1L, 2L, "USER", 3L, "comportement", "OPEN", null, null, null);
        when(moderationService.create(body)).thenReturn(response);
        assertThat(reportController.create(body)).isSameAs(response);
    }

    // Liste les signalements ouverts pour la modération.
    @Test
    void open_delegatesToService() {
        when(moderationService.openReports()).thenReturn(List.of());
        assertThat(reportController.open()).isEmpty();
        verify(moderationService).openReports();
    }

    // Clôture un signalement avec la décision admin.
    @Test
    void decide_delegatesToService() {
        DecideRequest body = new DecideRequest("VALIDATED");
        ReportResponse response =
                new ReportResponse(1L, 2L, "USER", 3L, "comportement", "CLOSED", "VALIDATED", 9L, null);
        when(moderationService.decide(1L, body)).thenReturn(response);
        assertThat(reportController.decide(1L, body)).isSameAs(response);
    }
}
