package com.barkwalk.walk.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.NotificationService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Rappel la veille aux inscrits CONFIRMED.
@Service
public class WalkTomorrowReminderService {
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public WalkTomorrowReminderService(
            WalkRepository walkRepository,
            BookingRepository bookingRepository,
            NotificationService notificationService) {
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public int sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        Instant from = tomorrow.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = tomorrow.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int count = 0;
        for (Walk walk : walkRepository.findPublishedBetween(from, to)) {
            Set<Long> notified = new HashSet<>();
            for (Booking booking : bookingRepository.findByWalkId(walk.getId())) {
                if (!"CONFIRMED".equals(booking.getStatus())) {
                    continue;
                }
                if (!notified.add(booking.getUserId())) {
                    continue;
                }
                notificationService.notifyWalkTomorrow(walk.getId(), booking.getUserId(), walk.getTitle());
                count++;
            }
        }
        return count;
    }
}
