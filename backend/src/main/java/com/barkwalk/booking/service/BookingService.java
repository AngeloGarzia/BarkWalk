package com.barkwalk.booking.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WalkRepository walkRepository;
    private final DogRepository dogRepository;

    public BookingService(BookingRepository bookingRepository, WaitlistEntryRepository waitlistEntryRepository,
                          WalkRepository walkRepository, DogRepository dogRepository) {
        this.bookingRepository = bookingRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.walkRepository = walkRepository;
        this.dogRepository = dogRepository;
    }

    public record BookingRequest(Long walkId, Long dogId, boolean communityAck, boolean trial) {}
    public record BookingResponse(Long id, Long walkId, Long dogId, String status, int priceCents,
                                  boolean paymentRequired, Instant confirmedAt) {}

    @Transactional
    public BookingResponse create(BookingRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Walk walk = walkRepository.findByIdForUpdate(request.walkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Walk not found"));
        if (!"PUBLISHED".equals(walk.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Walk not bookable");
        }
        Dog dog = dogRepository.findById(request.dogId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dog not found"));
        if (!dog.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dog not owned");
        }
        if (!"VALIDATED".equals(dog.getDossierStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog dossier not validated");
        }
        if (!contains(walk.getAcceptedSizes(), dog.getSizeCategory())
                || !contains(walk.getAcceptedEnergy(), dog.getEnergyLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog incompatible with walk profile");
        }
        if (walk.isCommunityDisclaimerRequired() && !request.communityAck()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Community disclaimer required");
        }
        if ("COMMUNITY".equals(walk.getWalkType()) && !SecurityUtils.currentUser().getRoles().contains(Role.MEMBER)
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership required");
        }
        if (bookingRepository.existsByWalkIdAndDogIdAndStatusNot(walk.getId(), dog.getId(), "CANCELLED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already booked");
        }

        long occupied = bookingRepository.countByWalkIdAndStatusIn(walk.getId(),
                List.of("CONFIRMED", "PENDING_PAYMENT"));
        if (occupied >= walk.getCapacity()) {
            WaitlistEntry entry = new WaitlistEntry();
            entry.setWalkId(walk.getId());
            entry.setUserId(userId);
            entry.setDogId(dog.getId());
            entry.setPosition((int) waitlistEntryRepository.countByWalkId(walk.getId()) + 1);
            entry.setStatus("WAITING");
            waitlistEntryRepository.save(entry);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Walk full — added to waitlist");
        }

        boolean member = SecurityUtils.currentUser().getRoles().contains(Role.MEMBER)
                || SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        int price = 0;
        boolean paymentRequired = false;
        if ("INDEP_GUIDED".equals(walk.getWalkType())) {
            price = walk.getPriceCents();
            paymentRequired = price > 0;
        } else if ("STAFF_GUIDED".equals(walk.getWalkType()) && !member && !request.trial()) {
            price = walk.getNonMemberPriceCents() != null ? walk.getNonMemberPriceCents() : walk.getPriceCents();
            paymentRequired = price > 0;
        }

        Booking booking = new Booking();
        booking.setWalkId(walk.getId());
        booking.setUserId(userId);
        booking.setDogId(dog.getId());
        booking.setCommunityAck(request.communityAck());
        booking.setTrial(request.trial());
        booking.setPriceCents(price);
        booking.setPaymentRequired(paymentRequired);
        if (paymentRequired) {
            booking.setStatus("PENDING_PAYMENT");
        } else {
            booking.setStatus("CONFIRMED");
            booking.setConfirmedAt(Instant.now());
        }
        booking = bookingRepository.save(booking);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine() {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookingResponse cancel(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!booking.getUserId().equals(SecurityUtils.currentUserId())
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        booking.setStatus("CANCELLED");
        booking.setCancelledAt(Instant.now());
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse confirmPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(Instant.now());
        return toResponse(booking);
    }

    private boolean contains(String[] values, String value) {
        if (values == null || value == null) {
            return false;
        }
        return Arrays.stream(values).anyMatch(v -> v != null && v.equalsIgnoreCase(value));
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getWalkId(), b.getDogId(), b.getStatus(),
                b.getPriceCents(), b.isPaymentRequired(), b.getConfirmedAt());
    }
}
