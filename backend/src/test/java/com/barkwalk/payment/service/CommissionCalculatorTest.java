package com.barkwalk.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionCalculatorTest {

    @Mock
    private BusinessSettingsService businessSettingsService;

    @InjectMocks
    private CommissionCalculator calculator;

    @Test
    void commissionCents_usesConfiguredPercent() {
        when(businessSettingsService.indepCommissionPercent()).thenReturn(15);
        assertThat(calculator.commissionCents(2000)).isEqualTo(300);
        assertThat(calculator.walkerNetCents(2000)).isEqualTo(1700);
    }

    @Test
    void commissionCents_zeroWhenAmountZero() {
        assertThat(calculator.commissionCents(0)).isEqualTo(0);
    }
}
