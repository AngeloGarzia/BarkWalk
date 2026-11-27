package com.barkwalk.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.service.BookingService;
import com.barkwalk.booking.service.WaitlistService;
import com.barkwalk.dog.service.DocumentReminderService;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.membership.service.MembershipService;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.walk.service.WalkTomorrowReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessJobsSchedulerTest {

    @Mock
    private BookingService bookingService;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private DogDossierService dogDossierService;
    @Mock
    private MembershipService membershipService;
    @Mock
    private MessagingService messagingService;
    @Mock
    private DocumentReminderService documentReminderService;
    @Mock
    private WalkTomorrowReminderService walkTomorrowReminderService;

    @InjectMocks
    private BusinessJobsScheduler scheduler;

    // Délègue l'exécution des tâches fréquentes.
    @Test
    void runFrequentJobs_delegates() {
        when(bookingService.expireUnpaidBookings()).thenReturn(1);
        when(waitlistService.expireOffers()).thenReturn(2);
        scheduler.runFrequentJobs();
        verify(bookingService).expireUnpaidBookings();
        verify(waitlistService).expireOffers();
    }

    // Exécute les tâches quotidiennes et envoie les rappels.
    @Test
    void runDailyJobs_sendsReminders() {
        when(dogDossierService.expireOverdueDossiers()).thenReturn(0);
        when(membershipService.expireOverdue()).thenReturn(0);
        when(messagingService.archiveGroupThreadsOlderThanDays(7)).thenReturn(0);
        when(documentReminderService.sendDueReminders()).thenReturn(3);
        when(walkTomorrowReminderService.sendTomorrowReminders()).thenReturn(2);
        scheduler.runDailyJobs();
        verify(documentReminderService).sendDueReminders();
        verify(walkTomorrowReminderService).sendTomorrowReminders();
    }
}
