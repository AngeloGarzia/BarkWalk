package com.barkwalk.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// R6 : libère périodiquement les places non payées.
@Component
public class PendingPaymentExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(PendingPaymentExpiryScheduler.class);

    private final BookingService bookingService;

    public PendingPaymentExpiryScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${barkwalk.jobs.pending-payment-ms:60000}")
    public void expire() {
        int count = bookingService.expireUnpaidBookings();
        if (count > 0) {
            log.info("R6: released {} unpaid booking(s)", count);
        }
    }
}
