package com.barkwalk.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.BusinessSettingsKeys;
import com.barkwalk.admin.domain.Setting;
import com.barkwalk.admin.repository.SettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BusinessSettingsServiceTest {

    @Mock
    private SettingRepository settingRepository;

    @InjectMocks
    private BusinessSettingsService businessSettingsService;

    // Utilise les valeurs par défaut si paramètres manquants.
    @Test
    void getInt_usesDefaultWhenMissing() {
        when(settingRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(businessSettingsService.cancellationFreeHours()).isEqualTo(24);
        assertThat(businessSettingsService.pendingPaymentMinutes()).isEqualTo(15);
        assertThat(businessSettingsService.waitlistOfferHours()).isEqualTo(2);
        assertThat(businessSettingsService.communityWalksThreshold()).isEqualTo(3);
        assertThat(businessSettingsService.communityCapacityMax()).isEqualTo(8);
        assertThat(businessSettingsService.indepCommissionPercent()).isEqualTo(15);
        assertThat(businessSettingsService.reviewWindowDays()).isEqualTo(14);
        assertThat(businessSettingsService.orgMaxReports()).isEqualTo(2);
        assertThat(businessSettingsService.orgMinRating()).isEqualTo(3);
        assertThat(businessSettingsService.orgMinReviews()).isEqualTo(5);
        assertThat(businessSettingsService.docReminderJ30Days()).isEqualTo(30);
        assertThat(businessSettingsService.docReminderJ7Days()).isEqualTo(7);
    }

    // Lit la valeur stockée en base de données.
    @Test
    void getInt_readsStoredValue() {
        Setting setting = new Setting();
        setting.setKey(BusinessSettingsKeys.PENDING_PAYMENT_MINUTES);
        setting.setValue("20");
        when(settingRepository.findById(BusinessSettingsKeys.PENDING_PAYMENT_MINUTES))
                .thenReturn(Optional.of(setting));
        assertThat(businessSettingsService.pendingPaymentMinutes()).isEqualTo(20);
    }

    // Persiste les paramètres connus en base.
    @Test
    void update_persistsKnownKey() {
        when(settingRepository.findById(BusinessSettingsKeys.REVIEW_WINDOW_DAYS)).thenReturn(Optional.empty());
        when(settingRepository.save(any(Setting.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = businessSettingsService.update(BusinessSettingsKeys.REVIEW_WINDOW_DAYS, "21");
        assertThat(response.key()).isEqualTo(BusinessSettingsKeys.REVIEW_WINDOW_DAYS);
        assertThat(response.value()).isEqualTo("21");
        verify(settingRepository).save(any(Setting.class));
    }

    // Refuse les paramètres inconnus.
    @Test
    void update_rejectsUnknownKey() {
        assertThatThrownBy(() -> businessSettingsService.update("not_a_real_setting", "1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // Refuse les pourcentages de commission invalides.
    @Test
    void update_rejectsInvalidCommission() {
        assertThatThrownBy(
                        () -> businessSettingsService.update(BusinessSettingsKeys.INDEP_COMMISSION_PERCENT, "150"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // Liste tous les paramètres métier disponibles.
    @Test
    void listBusinessSettings_returnsAllKeys() {
        when(settingRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(businessSettingsService.listBusinessSettings()).hasSize(13);
    }
}
