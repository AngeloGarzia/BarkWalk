package com.barkwalk.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// R7 : expire les offres de liste d'attente sans réponse.
@Component
public class WaitlistOfferScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitlistOfferScheduler.class);

    private final WaitlistService waitlistService;

    public WaitlistOfferScheduler(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @Scheduled(fixedDelayString = "${barkwalk.jobs.waitlist-offer-ms:60000}")
    public void expireOffers() {
        int count = waitlistService.expireOffers();
        if (count > 0) {
            log.info("R7: expired {} waitlist offer(s)", count);
        }
    }
}
