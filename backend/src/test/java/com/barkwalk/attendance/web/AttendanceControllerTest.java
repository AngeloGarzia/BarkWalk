package com.barkwalk.attendance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.attendance.dto.AttendanceDtos.PendingRollcallWalk;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.RosterResponse;
import com.barkwalk.attendance.service.AttendanceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    // Expose les balades en attente de pointage pour le menu Balades.
    @Test
    void pending_delegatesToService() {
        List<PendingRollcallWalk> pending = List.of(new PendingRollcallWalk(
                1L, "Balade", "Lyon", Instant.now(), 60, Instant.now(), "IN_PROGRESS", 3));
        when(attendanceService.pendingRollcalls()).thenReturn(pending);
        assertThat(attendanceController.pending()).isEqualTo(pending);
    }

    // Retourne la feuille de présence d'une balade.
    @Test
    void roster_delegatesToService() {
        RosterResponse roster = new RosterResponse(1L, "Balade", Instant.now(), Instant.now(), null, List.of());
        when(attendanceService.roster(1L)).thenReturn(roster);
        assertThat(attendanceController.roster(1L)).isSameAs(roster);
    }

    // Enregistre le pointage collectif après la fin de la balade.
    @Test
    void rollcall_delegatesToService() {
        RollcallRequest request = new RollcallRequest(List.of());
        RollcallResponse response = new RollcallResponse(1L, Instant.now(), 2, 1);
        when(attendanceService.submitRollcall(1L, request)).thenReturn(response);
        assertThat(attendanceController.rollcall(1L, request)).isSameAs(response);
        verify(attendanceService).submitRollcall(1L, request);
    }

    // Enregistre le statut au départ.
    @Test
    void departure_delegatesToService() {
        PointRequest request = new PointRequest(10L, "PRESENT");
        attendanceController.departure(request);
        verify(attendanceService).departure(request);
    }

    // Enregistre le statut au retour.
    @Test
    void markReturn_delegatesToService() {
        PointRequest request = new PointRequest(10L, "ABSENT");
        attendanceController.markReturn(request);
        verify(attendanceService).markReturn(request);
    }
}
