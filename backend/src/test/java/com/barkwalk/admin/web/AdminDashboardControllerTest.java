package com.barkwalk.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.dto.AdminDtos.DashboardResponse;
import com.barkwalk.admin.dto.AdminDtos.FinanceResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingUpdateRequest;
import com.barkwalk.admin.service.AdminService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    // Affiche les indicateurs du tableau de bord admin.
    @Test
    void dashboard_delegatesToService() {
        DashboardResponse response = new DashboardResponse(10, 5, 20, 3, 1000, 150);
        when(adminService.dashboard()).thenReturn(response);
        assertThat(adminDashboardController.dashboard()).isSameAs(response);
    }

    // Lit les paramètres métier configurables.
    @Test
    void settings_delegatesToService() {
        when(adminService.settings()).thenReturn(List.of());
        assertThat(adminDashboardController.settings()).isEmpty();
    }

    // Met à jour un paramètre métier.
    @Test
    void updateSetting_delegatesToService() {
        SettingResponse response = new SettingResponse("pending_payment_minutes", "15", "minutes", null);
        when(adminService.updateSetting("pending_payment_minutes", "20")).thenReturn(response);
        assertThat(adminDashboardController.updateSetting(
                        "pending_payment_minutes", new SettingUpdateRequest("20")))
                .isSameAs(response);
        verify(adminService).updateSetting("pending_payment_minutes", "20");
    }

    // Expose la vue finance admin.
    @Test
    void finance_delegatesToService() {
        FinanceResponse response = new FinanceResponse(List.of());
        when(adminService.finance()).thenReturn(response);
        assertThat(adminDashboardController.finance()).isSameAs(response);
    }
}
