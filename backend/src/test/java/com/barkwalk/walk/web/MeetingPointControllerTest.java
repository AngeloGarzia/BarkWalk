package com.barkwalk.walk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointCreateRequest;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointResponse;
import com.barkwalk.walk.service.MeetingPointService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingPointControllerTest {

    @Mock
    private MeetingPointService meetingPointService;

    @InjectMocks
    private MeetingPointController meetingPointController;

    // Liste les points de rendez-vous actifs d'une commune.
    @Test
    void list_delegatesToService() {
        when(meetingPointService.listByCity(1L)).thenReturn(List.of());
        assertThat(meetingPointController.list(1L)).isEmpty();
        verify(meetingPointService).listByCity(1L);
    }

    // Crée un point de rendez-vous depuis la carte.
    @Test
    void create_delegatesToService() {
        MeetingPointCreateRequest request =
                new MeetingPointCreateRequest(1L, "Parc", "1 rue Test", 45.7, 4.8);
        MeetingPointResponse response = new MeetingPointResponse(9L, 1L, "Parc", "1 rue Test", 45.7, 4.8);
        when(meetingPointService.create(request)).thenReturn(response);
        assertThat(meetingPointController.create(request)).isSameAs(response);
    }
}
