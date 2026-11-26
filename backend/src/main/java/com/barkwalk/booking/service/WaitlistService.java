package com.barkwalk.booking.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.NotificationService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// R7 : offre de place au premier de la file, délai configurable, cascade si expiration.
@Service
public class WaitlistService {
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final BookingRepository bookingRepository;
    private final WalkRepository walkRepository;
    private final BusinessSettingsService businessSettingsService;
    private final NotificationService notificationService;

    public WaitlistService(
            WaitlistEntryRepository waitlistEntryRepository,
            BookingRepository bookingRepository,
            WalkRepository walkRepository,
            BusinessSettingsService businessSettingsService,
            NotificationService notificationService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.bookingRepository = bookingRepository;
        this.walkRepository = walkRepository;
        this.businessSettingsService = businessSettingsService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void offerNext(Long walkId) {
        expireOffersForWalk(walkId);
        long occupied = bookingRepository.countByWalkIdAndStatusIn(
                walkId, List.of("CONFIRMED", "PENDING_PAYMENT"));
        Walk walk = walkRepository.findById(walkId).orElse(null);
        if (walk == null || occupied >= walk.getCapacity()) {
            return;
        }
        if (!waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(walkId, "OFFERED").isEmpty()) {
            return;
        }
        List<WaitlistEntry> waiting =
                waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(walkId, "WAITING");
        if (waiting.isEmpty()) {
            return;
        }
        WaitlistEntry next = waiting.getFirst();
        Instant now = Instant.now();
        next.setStatus("OFFERED");
        next.setOfferedAt(now);
        next.setOfferExpiresAt(now.plus(Duration.ofHours(businessSettingsService.waitlistOfferHours())));
        waitlistEntryRepository.save(next);
        notificationService.notifyWaitlistOffer(walkId, next.getUserId(), next.getId());
    }

    @Transactional
    public BookingResponse acceptOffer(Long entryId) {
        WaitlistEntry entry = waitlistEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!entry.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"OFFERED".equals(entry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer not available");
        }
        if (entry.getOfferExpiresAt() != null && Instant.now().isAfter(entry.getOfferExpiresAt())) {
            entry.setStatus("EXPIRED");
            waitlistEntryRepository.save(entry);
            offerNext(entry.getWalkId());
            throw new ResponseStatusException(HttpStatus.GONE, "Offer expired");
        }
        Walk walk = walkRepository.findByIdForUpdate(entry.getWalkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long occupied = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT"));
        if (occupied >= walk.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Walk full again");
        }
        Booking booking = new Booking();
        booking.setWalkId(walk.getId());
        booking.setUserId(entry.getUserId());
        booking.setDogId(entry.getDogId());
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(Instant.now());
        booking.setPaymentRequired(false);
        booking.setPriceCents(0);
        booking.setCommunityAck("COMMUNITY".equals(walk.getWalkType()));
        booking = bookingRepository.save(booking);
        entry.setStatus("ACCEPTED");
        waitlistEntryRepository.save(entry);
        return new BookingResponse(
                booking.getId(),
                booking.getWalkId(),
                booking.getDogId(),
                booking.getStatus(),
                booking.getPriceCents(),
                booking.isPaymentRequired(),
                booking.getConfirmedAt());
    }

    @Transactional
    public int expireOffers() {
        Instant now = Instant.now();
        int count = 0;
        for (WaitlistEntry entry : waitlistEntryRepository.findByStatusAndOfferExpiresAtBefore("OFFERED", now)) {
            entry.setStatus("EXPIRED");
            waitlistEntryRepository.save(entry);
            offerNext(entry.getWalkId());
            count++;
        }
        return count;
    }

    private void expireOffersForWalk(Long walkId) {
        Instant now = Instant.now();
        for (WaitlistEntry entry :
                waitlistEntryRepository.findByWalkIdAndStatusOrderByPositionAsc(walkId, "OFFERED")) {
            if (entry.getOfferExpiresAt() != null && now.isAfter(entry.getOfferExpiresAt())) {
                entry.setStatus("EXPIRED");
                waitlistEntryRepository.save(entry);
            }
        }
    }
}
