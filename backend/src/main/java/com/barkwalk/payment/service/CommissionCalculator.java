package com.barkwalk.payment.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import org.springframework.stereotype.Component;

// R12 : commission % indépendants depuis settings (montant plateforme, reste à l'indépendant).
@Component
public class CommissionCalculator {
    private final BusinessSettingsService businessSettingsService;

    public CommissionCalculator(BusinessSettingsService businessSettingsService) {
        this.businessSettingsService = businessSettingsService;
    }

    public int commissionCents(int amountCents) {
        if (amountCents <= 0) {
            return 0;
        }
        int percent = businessSettingsService.indepCommissionPercent();
        return Math.toIntExact(Math.round(amountCents * (percent / 100.0)));
    }

    public int walkerNetCents(int amountCents) {
        return Math.max(0, amountCents - commissionCents(amountCents));
    }
}
