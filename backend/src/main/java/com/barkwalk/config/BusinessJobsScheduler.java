package com.barkwalk.config;

import com.barkwalk.admin.BusinessSettingsKeys;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.booking.service.WaitlistService;
import com.barkwalk.dog.service.DocumentReminderService;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.membership.service.MembershipService;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.walk.service.WalkTomorrowReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatismes planifiés :
 * R6 unpaid, R7 waitlist, R14 membership, R16 dossier/docs, archive messages, rappel veille.
 */
@Component
public class BusinessJobsScheduler {
    private static final Logger log = LoggerFactory.getLogger(BusinessJobsScheduler.class);

    private final BookingService bookingService;
    private final WaitlistService waitlistService;
    private final DogDossierService dogDossierService;
    private final MembershipService membershipService;
    private final MessagingService messagingService;
    private final DocumentReminderService documentReminderService;
    private final WalkTomorrowReminderService walkTomorrowReminderService;

    public BusinessJobsScheduler(
            BookingService bookingService,
            WaitlistService waitlistService,
            DogDossierService dogDossierService,
            MembershipService membershipService,
            MessagingService messagingService,
            DocumentReminderService documentReminderService,
            WalkTomorrowReminderService walkTomorrowReminderService) {
        this.bookingService = bookingService;
        this.waitlistService = waitlistService;
        this.dogDossierService = dogDossierService;
        this.membershipService = membershipService;
        this.messagingService = messagingService;
        this.documentReminderService = documentReminderService;
        this.walkTomorrowReminderService = walkTomorrowReminderService;
    }

    @Scheduled(fixedDelayString = "${barkwalk.jobs.core-ms:60000}")
    public void runFrequentJobs() {
        int unpaid = bookingService.expireUnpaidBookings();
        int offers = waitlistService.expireOffers();
        if (unpaid + offers > 0) {
            log.info("Jobs: unpaid={} waitlistOffers={}", unpaid, offers);
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void runDailyJobs() {
        int dossiers = dogDossierService.expireOverdueDossiers();
        int memberships = membershipService.expireOverdue();
        int archived = messagingService.archiveGroupThreadsOlderThanDays(7);
        int docReminders = documentReminderService.sendDueReminders();
        int walkReminders = walkTomorrowReminderService.sendTomorrowReminders();
        log.info(
                "Daily jobs: dossiers={} memberships={} archived={} docReminders={} walkReminders={} ({})",
                dossiers,
                memberships,
                archived,
                docReminders,
                walkReminders,
                BusinessSettingsKeys.DOC_REMINDER_J30_DAYS);
    }
}
