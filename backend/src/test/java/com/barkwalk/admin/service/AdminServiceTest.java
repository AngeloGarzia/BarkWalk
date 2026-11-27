package com.barkwalk.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.dto.AdminDtos.DashboardResponse;
import com.barkwalk.admin.dto.AdminDtos.FinanceResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingResponse;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.service.PaymentService;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.repository.WalkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private WalkRepository walkRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BusinessSettingsService businessSettingsService;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private AdminService adminService;

    /** Agrège les compteurs et somme uniquement les paiements SUCCEEDED. */
    @Test
    void dashboard_aggregatesStats() {
        Payment succeeded = new Payment();
        succeeded.setStatus("SUCCEEDED");
        succeeded.setAmountCents(3000);
        succeeded.setCommissionCents(450);
        succeeded.setUserId(1L);
        succeeded.setPaymentType("BOOKING");

        Payment failed = new Payment();
        failed.setStatus("FAILED");
        failed.setAmountCents(1000);
        failed.setCommissionCents(0);
        failed.setUserId(2L);
        failed.setPaymentType("BOOKING");

        when(userRepository.count()).thenReturn(42L);
        when(walkRepository.count()).thenReturn(15L);
        when(bookingRepository.count()).thenReturn(100L);
        when(membershipRepository.count()).thenReturn(20L);
        when(paymentRepository.findAll()).thenReturn(List.of(succeeded, failed));

        DashboardResponse dashboard = adminService.dashboard();

        assertThat(dashboard.users()).isEqualTo(42L);
        assertThat(dashboard.walks()).isEqualTo(15L);
        assertThat(dashboard.bookings()).isEqualTo(100L);
        assertThat(dashboard.memberships()).isEqualTo(20L);
        assertThat(dashboard.revenueCents()).isEqualTo(3000);
        assertThat(dashboard.commissionCents()).isEqualTo(450);
    }

    /** Délègue la liste des paramètres métier au service dédié. */
    @Test
    void settings_delegatesToBusinessSettings() {
        SettingResponse setting = new SettingResponse(
                "cancellation_free_hours", "24", "Annulation gratuite (heures)", null);
        when(businessSettingsService.listBusinessSettings()).thenReturn(List.of(setting));

        List<SettingResponse> result = adminService.settings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("cancellation_free_hours");
        assertThat(result.get(0).value()).isEqualTo("24");
    }

    /** Délègue la mise à jour d'un paramètre au service dédié et retourne la réponse. */
    @Test
    void updateSetting_delegatesToBusinessSettings() {
        SettingResponse updated = new SettingResponse(
                "cancellation_free_hours", "48", "Annulation gratuite (heures)", null);
        when(businessSettingsService.update("cancellation_free_hours", "48")).thenReturn(updated);

        SettingResponse result = adminService.updateSetting("cancellation_free_hours", "48");

        assertThat(result.value()).isEqualTo("48");
    }

    /** Retourne la liste des paiements via PaymentService. */
    @Test
    void finance_delegatesToPaymentService() {
        when(paymentService.allForAdmin()).thenReturn(List.of());

        FinanceResponse result = adminService.finance();

        assertThat(result.payments()).isEmpty();
    }
}
