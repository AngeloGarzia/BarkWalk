package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.NotificationService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalkTomorrowReminderServiceTest {

    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WalkTomorrowReminderService service;

    // Notifie une seule fois les utilisateurs confirmés.
    @Test
    void sendTomorrowReminders_notifiesConfirmedUsersOnce() {
        Walk walk = new Walk();
        setId(walk, 7L);
        walk.setTitle("Parc");
        when(walkRepository.findPublishedBetween(any(Instant.class), any(Instant.class))).thenReturn(List.of(walk));

        Booking confirmed = booking(1L, 7L, 11L, "CONFIRMED");
        Booking duplicate = booking(2L, 7L, 11L, "CONFIRMED");
        Booking pending = booking(3L, 7L, 12L, "PENDING_PAYMENT");
        when(bookingRepository.findByWalkId(7L)).thenReturn(List.of(confirmed, duplicate, pending));

        assertThat(service.sendTomorrowReminders()).isEqualTo(1);
        verify(notificationService).notifyWalkTomorrow(eq(7L), eq(11L), eq("Parc"));
        verify(notificationService, never()).notifyWalkTomorrow(eq(7L), eq(12L), any());
    }

    // Retourne zéro si aucune balade prévue demain.
    @Test
    void sendTomorrowReminders_returnsZeroWhenNoWalks() {
        when(walkRepository.findPublishedBetween(any(Instant.class), any(Instant.class))).thenReturn(List.of());
        assertThat(service.sendTomorrowReminders()).isZero();
    }

    private static Booking booking(long id, long walkId, long userId, String status) {
        Booking b = new Booking();
        setId(b, id);
        b.setWalkId(walkId);
        b.setUserId(userId);
        b.setStatus(status);
        return b;
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
