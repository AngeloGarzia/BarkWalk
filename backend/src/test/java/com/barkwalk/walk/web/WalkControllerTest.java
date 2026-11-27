package com.barkwalk.walk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantsResponse;
import com.barkwalk.walk.service.WalkPhotoService;
import com.barkwalk.walk.service.WalkService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalkControllerTest {

    @Mock
    private WalkService walkService;

    @Mock
    private WalkPhotoService walkPhotoService;

    @InjectMocks
    private WalkController walkController;

    // Catalogue public filtré par ville et type.
    @Test
    void catalogue_delegatesToService() {
        when(walkService.catalogue(1L, "COMMUNITY", null)).thenReturn(List.of());
        assertThat(walkController.catalogue(1L, "COMMUNITY", null)).isEmpty();
        verify(walkService).catalogue(1L, "COMMUNITY", null);
    }

    // Liste les balades de l'organisateur ou encadrant connecté.
    @Test
    void mine_delegatesToService() {
        when(walkService.mine()).thenReturn(List.of());
        assertThat(walkController.mine()).isEmpty();
    }

    // Vue admin de l'ensemble des balades.
    @Test
    void adminOverview_delegatesToService() {
        when(walkService.adminOverview(null, "PUBLISHED")).thenReturn(List.of());
        assertThat(walkController.adminOverview(null, "PUBLISHED")).isEmpty();
    }

    // Détail d'une balade.
    @Test
    void get_delegatesToService() {
        WalkResponse response = org.mockito.Mockito.mock(WalkResponse.class);
        when(walkService.get(7L)).thenReturn(response);
        assertThat(walkController.get(7L)).isSameAs(response);
    }

    // Participants d'une balade (pseudos selon droits).
    @Test
    void participants_delegatesToService() {
        ParticipantsResponse response = new ParticipantsResponse(7L, List.of());
        when(walkService.participants(7L)).thenReturn(response);
        assertThat(walkController.participants(7L)).isSameAs(response);
    }

    // Photos d'une balade.
    @Test
    void photos_delegatesToPhotoService() {
        when(walkPhotoService.list(7L, "PROMO")).thenReturn(List.of());
        assertThat(walkController.photos(7L, "PROMO")).isEmpty();
        verify(walkPhotoService).list(7L, "PROMO");
    }
}
