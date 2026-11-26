package com.barkwalk.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.NotificationService;
import com.barkwalk.common.Role;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BookingResponseFactory bookingResponseFactory;
    @Mock
    private MessagingService messagingService;

    @InjectMocks
    private WaitlistService waitlistService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @org.junit.jupiter.api.BeforeEach
    void stubFactory() {
        org.mockito.Mockito.lenient()
                .when(bookingResponseFactory.from(any(Booking.class), any(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenAnswer(inv -> {
                    Booking b = inv.getArgument(0);
                    return new com.barkwalk.booking.dto.BookingDtos.BookingResponse(
                            b.getId(),
                            b.getWalkId(),
                            b.getDogId(),
                            b.getStatus(),
                            b.getPriceCents(),
                            b.isPaymentRequired(),
                            b.getConfirmedAt(),
                            b.getPaymentExpiresAt(),
                            b.isTrial(),
                            b.isCommunityAck(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
                });
    }

    @Test
    void offerNext_offersFirstWaiting() {
        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(2);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(1L);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of());
        WaitlistEntry entry = new WaitlistEntry();
        setId(entry, 5L);
        entry.setWalkId(10L);
        entry.setUserId(3L);
        entry.setDogId(7L);
        entry.setStatus("WAITING");
        entry.setPosition(1);
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "WAITING"))
                .thenReturn(List.of(entry));
        when(businessSettingsService.waitlistOfferHours()).thenReturn(2);
        when(waitlistEntryRepository.save(entry)).thenReturn(entry);

        waitlistService.offerNext(10L);

        assertThat(entry.getStatus()).isEqualTo("OFFERED");
        assertThat(entry.getOfferExpiresAt()).isNotNull();
        verify(notificationService).notifyWaitlistOffer(10L, 3L, 5L);
    }

    @Test
    void expireOffers_cascadesToNext() {
        WaitlistEntry expired = new WaitlistEntry();
        setId(expired, 1L);
        expired.setWalkId(10L);
        expired.setStatus("OFFERED");
        expired.setOfferExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(waitlistEntryRepository.findByStatusAndOfferExpiresAtBefore(eq("OFFERED"), any()))
                .thenReturn(List.of(expired));
        when(waitlistEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(1);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of());
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "WAITING"))
                .thenReturn(List.of());

        assertThat(waitlistService.expireOffers()).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void acceptOffer_createsConfirmedBooking() {
        SecurityTestSupport.authenticate(3L, "u@example.com", Role.USER, Role.MEMBER);
        WaitlistEntry entry = new WaitlistEntry();
        setId(entry, 5L);
        entry.setWalkId(10L);
        entry.setUserId(3L);
        entry.setDogId(7L);
        entry.setStatus("OFFERED");
        entry.setOfferExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(waitlistEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(2);
        walk.setWalkType("STAFF_GUIDED");
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            setId(b, 99L);
            return b;
        });
        when(waitlistEntryRepository.save(entry)).thenReturn(entry);

        var response = waitlistService.acceptOffer(5L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(entry.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void offerNext_noopWhenFullOrAlreadyOffered() {
        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(1);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(1L);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of());
        waitlistService.offerNext(10L);

        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        WaitlistEntry offered = new WaitlistEntry();
        offered.setStatus("OFFERED");
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of(offered));
        waitlistService.offerNext(10L);
    }

    @Test
    void offerNext_expiresStaleOfferBeforeSelectingNext() {
        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(2);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        WaitlistEntry stale = new WaitlistEntry();
        setId(stale, 1L);
        stale.setWalkId(10L);
        stale.setStatus("OFFERED");
        stale.setOfferExpiresAt(Instant.now().minusSeconds(30));
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of(stale))
                .thenReturn(List.of());

        WaitlistEntry waiting = new WaitlistEntry();
        setId(waiting, 2L);
        waiting.setWalkId(10L);
        waiting.setUserId(8L);
        waiting.setDogId(9L);
        waiting.setStatus("WAITING");
        waiting.setPosition(1);
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "WAITING"))
                .thenReturn(List.of(waiting));
        when(businessSettingsService.waitlistOfferHours()).thenReturn(2);
        when(waitlistEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        waitlistService.offerNext(10L);

        assertThat(stale.getStatus()).isEqualTo("EXPIRED");
        assertThat(waiting.getStatus()).isEqualTo("OFFERED");
    }

    @Test
    void acceptOffer_forbiddenForOtherUser() {
        SecurityTestSupport.authenticate(99L, "other@example.com", Role.USER);
        WaitlistEntry entry = new WaitlistEntry();
        setId(entry, 5L);
        entry.setUserId(3L);
        entry.setStatus("OFFERED");
        when(waitlistEntryRepository.findById(5L)).thenReturn(Optional.of(entry));
        assertThatThrownBy(() -> waitlistService.acceptOffer(5L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptOffer_rejectsExpired() {
        SecurityTestSupport.authenticate(3L, "u@example.com", Role.USER);
        WaitlistEntry entry = new WaitlistEntry();
        setId(entry, 5L);
        entry.setWalkId(10L);
        entry.setUserId(3L);
        entry.setStatus("OFFERED");
        entry.setOfferExpiresAt(Instant.now().minusSeconds(10));
        when(waitlistEntryRepository.findById(5L)).thenReturn(Optional.of(entry));
        when(waitlistEntryRepository.save(entry)).thenReturn(entry);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setCapacity(1);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "OFFERED"))
                .thenReturn(List.of());
        when(waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(10L, "WAITING"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> waitlistService.acceptOffer(5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GONE));
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
