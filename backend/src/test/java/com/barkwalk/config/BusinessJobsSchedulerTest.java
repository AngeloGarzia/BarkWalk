package com.barkwalk.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.booking.service.WaitlistService;
import com.barkwalk.common.NotificationService;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.membership.service.MembershipService;
import com.barkwalk.messaging.service.MessagingService;
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
    private BusinessSettingsService businessSettingsService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BusinessJobsScheduler scheduler;

    @Test
    void runFrequentJobs_delegates() {
        when(bookingService.expireUnpaidBookings()).thenReturn(1);
        when(waitlistService.expireOffers()).thenReturn(2);
        scheduler.runFrequentJobs();
        verify(bookingService).expireUnpaidBookings();
        verify(waitlistService).expireOffers();
    }

    @Test
    void runDailyJobs_usesDocReminderSettings() {
        when(dogDossierService.expireOverdueDossiers()).thenReturn(0);
        when(membershipService.expireOverdue()).thenReturn(0);
        when(messagingService.archiveGroupThreadsOlderThanDays(7)).thenReturn(0);
        when(businessSettingsService.docReminderJ30Days()).thenReturn(30);
        when(businessSettingsService.docReminderJ7Days()).thenReturn(7);
        scheduler.runDailyJobs();
        verify(notificationService).notifyDocReminderSweep(30, 7);
    }
}
