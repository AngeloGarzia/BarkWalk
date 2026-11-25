package com.barkwalk.booking.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Réservation transactionnelle : compatibilité chien, capacité, paiement éventuel, liste d'attente.
@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

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

    // Verrouille la balade le temps de contrôler capacité et doublons (évite le surbooking).
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
        if ("COMMUNITY".equals(walk.getWalkType()) && !request.communityAck()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Community disclaimer required");
        }
        if ("COMMUNITY".equals(walk.getWalkType()) && !SecurityUtils.currentUser().getRoles().contains(Role.MEMBER)
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership required");
        }
        if (bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(
                walk.getId(), dog.getId(), List.of("CANCELLED_FREE", "CANCELLED_LATE"))) {
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
            // Place libérable après 15 minutes sans paiement (job / libération R6).
            booking.setPaymentExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
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
        // Pour l'instant toute annulation utilisateur est marquée gratuite.
        // Le basculement tardif (comptage quota et seuil organisateur) se calcule à partir de starts_at.
        booking.setStatus("CANCELLED_FREE");
        booking.setCancelledAt(Instant.now());
        booking.setCancelledByUserId(SecurityUtils.currentUserId());
        booking.setCancelledByRole("USER");
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse confirmPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            // Paiement reçu pour une réservation déjà confirmée (rejeu) ou expirée/annulée :
            // on ne ressuscite pas la place, un remboursement manuel est nécessaire dans ce cas.
            if (!"CONFIRMED".equals(booking.getStatus())) {
                log.warn("Payment received for booking {} in status {}: refund required", bookingId, booking.getStatus());
            }
            return toResponse(booking);
        }
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
