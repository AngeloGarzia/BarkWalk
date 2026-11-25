package com.barkwalk.booking.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Libère régulièrement les réservations PENDING_PAYMENT dont la fenêtre de paiement est dépassée.
@Component
public class BookingExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${barkwalk.booking.payment-expiry-check-ms:60000}")
    public void releaseExpiredPayments() {
        int released = bookingService.expireUnpaidBookings(Instant.now());
        if (released > 0) {
            log.info("Released {} booking(s) whose payment window expired", released);
        }
    }
}
