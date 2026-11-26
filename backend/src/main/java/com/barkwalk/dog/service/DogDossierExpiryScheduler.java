package com.barkwalk.dog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// R16 : passe les dossiers en EXPIRED quand un document requis a dépassé expires_at.
@Component
public class DogDossierExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(DogDossierExpiryScheduler.class);

    private final DogDossierService dogDossierService;

    public DogDossierExpiryScheduler(DogDossierService dogDossierService) {
        this.dogDossierService = dogDossierService;
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void expireOverdue() {
        int count = dogDossierService.expireOverdueDossiers();
        if (count > 0) {
            log.info("R16: {} dog dossier(s) marked EXPIRED", count);
        }
    }
}
