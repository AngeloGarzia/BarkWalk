package com.barkwalk.booking.service;

import com.barkwalk.booking.WalkFullWaitlistedException;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.NotificationService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.service.DogWalkCompatibilityService;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Réservation transactionnelle : compatibilité chien, capacité, paiement éventuel, liste d'attente.
@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WalkRepository walkRepository;
    private final DogRepository dogRepository;
    private final DogDossierService dogDossierService;
    private final DogWalkCompatibilityService compatibilityService;
    private final BusinessSettingsService businessSettingsService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final WalkerProfileRepository walkerProfileRepository;
    private final WaitlistService waitlistService;
    private final BookingResponseFactory bookingResponseFactory;
    private final MessagingService messagingService;

    public BookingService(
            BookingRepository bookingRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            WalkRepository walkRepository,
            DogRepository dogRepository,
            DogDossierService dogDossierService,
            DogWalkCompatibilityService compatibilityService,
            BusinessSettingsService businessSettingsService,
            PaymentRepository paymentRepository,
            NotificationService notificationService,
            WalkerProfileRepository walkerProfileRepository,
            @Lazy WaitlistService waitlistService,
            BookingResponseFactory bookingResponseFactory,
            MessagingService messagingService) {
        this.bookingRepository = bookingRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.walkRepository = walkRepository;
        this.dogRepository = dogRepository;
        this.dogDossierService = dogDossierService;
        this.compatibilityService = compatibilityService;
        this.businessSettingsService = businessSettingsService;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.walkerProfileRepository = walkerProfileRepository;
        this.waitlistService = waitlistService;
        this.bookingResponseFactory = bookingResponseFactory;
        this.messagingService = messagingService;
    }

    // Verrouille la balade le temps de contrôler capacité et doublons (évite le surbooking).
    // R7 : WalkFullWaitlistedException ne doit pas annuler la transaction (entrée WAITING déjà sauvée).
    @Transactional(noRollbackFor = WalkFullWaitlistedException.class)
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
        // R2 / R16 : dossier validé + vaccins/RC non expirés (sinon EXPIRED / refus).
        dogDossierService.assertBookable(dog);
        // R3 : taille / énergie / sociabilité avec motif précis.
        compatibilityService.assertCompatible(walk, dog);
        if ("COMMUNITY".equals(walk.getWalkType()) && !request.communityAck()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Community disclaimer required");
        }
        if ("COMMUNITY".equals(walk.getWalkType()) && !SecurityUtils.currentUser().getRoles().contains(Role.MEMBER)
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership required");
        }
        // R1 : essai gratuit = 1 par compte, uniquement balade encadrée salarié.
        boolean trial = request.trial();
        if (trial) {
            if (!"STAFF_GUIDED".equals(walk.getWalkType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Free trial only on staff-guided walks");
            }
            if (bookingRepository.existsByUserIdAndTrialTrue(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Free trial already used");
            }
        }

        if (walk.getStartsAt() == null || !walk.getStartsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot book a past walk");
        }

        if (bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(
                walk.getId(), dog.getId(), List.of("CANCELLED_FREE", "CANCELLED_LATE"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already booked");
        }

        long occupied = bookingRepository.countByWalkIdAndStatusIn(walk.getId(),
                List.of("CONFIRMED", "PENDING_PAYMENT"));
        if (occupied >= walk.getCapacity()) {
            if (waitlistEntryRepository.existsByWalkIdAndDogIdAndStatusIn(
                    walk.getId(), dog.getId(), List.of("WAITING", "OFFERED"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already on waitlist");
            }
            WaitlistEntry entry = new WaitlistEntry();
            entry.setWalkId(walk.getId());
            entry.setUserId(userId);
            entry.setDogId(dog.getId());
            entry.setPosition((int) waitlistEntryRepository.countByWalkId(walk.getId()) + 1);
            entry.setStatus("WAITING");
            waitlistEntryRepository.saveAndFlush(entry);
            throw new WalkFullWaitlistedException();
        }

        boolean member = SecurityUtils.currentUser().getRoles().contains(Role.MEMBER)
                || SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        int price = 0;
        boolean paymentRequired = false;
        if ("INDEP_GUIDED".equals(walk.getWalkType())) {
            price = walk.getPriceCents();
            paymentRequired = price > 0;
        } else if ("STAFF_GUIDED".equals(walk.getWalkType()) && !member && !trial) {
            price = walk.getNonMemberPriceCents() != null ? walk.getNonMemberPriceCents() : walk.getPriceCents();
            paymentRequired = price > 0;
        }

        Booking booking = new Booking();
        booking.setWalkId(walk.getId());
        booking.setUserId(userId);
        booking.setDogId(dog.getId());
        booking.setCommunityAck(request.communityAck());
        booking.setTrial(trial);
        booking.setPriceCents(price);
        booking.setPaymentRequired(paymentRequired);
        if (paymentRequired) {
            booking.setStatus("PENDING_PAYMENT");
            int minutes = businessSettingsService.pendingPaymentMinutes();
            booking.setPaymentExpiresAt(Instant.now().plus(Duration.ofMinutes(minutes)));
        } else {
            booking.setStatus("CONFIRMED");
            booking.setConfirmedAt(Instant.now());
        }
        booking = bookingRepository.save(booking);
        if ("CONFIRMED".equals(booking.getStatus())) {
            messagingService.addMemberToWalkThread(booking.getWalkId(), booking.getUserId());
        }
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
        assertCancellable(booking);
        Walk walk = walkRepository.findById(booking.getWalkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Walk not found"));
        Instant now = Instant.now();
        if (walk.getStartsAt() != null && !now.isBefore(walk.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel after walk start");
        }
        int freeHours = businessSettingsService.cancellationFreeHours();
        Instant freeUntil = walk.getStartsAt().minus(Duration.ofHours(freeHours));
        booking.setCancelledAt(now);
        booking.setCancelledByUserId(SecurityUtils.currentUserId());
        booking.setCancelledByRole("USER");
        if (now.isBefore(freeUntil)) {
            // R4 : annulation gratuite jusqu'à X h avant.
            booking.setStatus("CANCELLED_FREE");
            // Remboursement utilisateur : non branché Stripe (voir refundFully stub).
        } else {
            // R4 : au-delà — pas de remboursement, balade comptée.
            booking.setStatus("CANCELLED_LATE");
            booking.setCountsTowardOrgThreshold(true);
            booking.setCountsTowardMembershipQuota(true);
        }
        bookingRepository.save(booking);
        waitlistService.offerNext(walk.getId());
        return toResponse(booking);
    }

    /** R5 : annulation organisateur / encadrant / admin → remboursement intégral + email. */
    @Transactional
    public BookingResponse cancelByStaff(Long id, String reason) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCancellable(booking);
        Walk walk = walkRepository.findById(booking.getWalkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Walk not found"));
        String role = requireStaffCanceller(walk);
        booking.setStatus("CANCELLED_FREE");
        booking.setCancelledAt(Instant.now());
        booking.setCancelledByUserId(SecurityUtils.currentUserId());
        booking.setCancelledByRole(role);
        booking.setCancellationReason(reason);
        booking.setCountsTowardOrgThreshold(false);
        booking.setCountsTowardMembershipQuota(false);
        refundFully(booking, reason != null ? reason : "cancelled_by_staff");
        bookingRepository.save(booking);
        notificationService.notifyWalkCancelledByStaff(walk.getId(), booking.getUserId(), reason);
        waitlistService.offerNext(walk.getId());
        return toResponse(booking);
    }

    private void assertCancellable(Booking booking) {
        String status = booking.getStatus();
        if (!"CONFIRMED".equals(status) && !"PENDING_PAYMENT".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking cannot be cancelled in status " + status);
        }
    }

    private String requireStaffCanceller(Walk walk) {
        var principal = SecurityUtils.currentUser();
        if (principal.getRoles().contains(Role.ADMIN)) {
            return "ADMIN";
        }
        if (walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(principal.getId())) {
            return "ORGANIZER";
        }
        if (walk.getWalkerId() != null) {
            boolean assigned = walkerProfileRepository.findByUserId(principal.getId())
                    .map(wp -> walk.getWalkerId().equals(wp.getId()))
                    .orElse(false);
            if (assigned) {
                return "WALKER";
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to cancel this booking");
    }

    /**
     * Remboursement intégral — <b>stub</b> tant que Stripe Refund n'est pas branché.
     * Enregistre un marquage local {@code stub-full-refund} : ne pas considérer comme prod-ready.
     */
    private void refundFully(Booking booking, String reason) {
        if (!booking.isPaymentRequired() || booking.getPriceCents() <= 0) {
            return;
        }
        paymentRepository.findByBookingId(booking.getId()).ifPresent(payment -> {
            if (payment.getRefundedAt() != null) {
                return;
            }
            payment.setRefundAmountCents(payment.getAmountCents());
            payment.setRefundedAt(Instant.now());
            payment.setRefundReason(reason);
            // TODO(prod): appeler Stripe Refund API ; aujourd'hui aucun flux d'argent réel.
            payment.setRefundProviderId("stub-full-refund");
            paymentRepository.save(payment);
        });
    }

    /** Confirmation serveur uniquement (webhook) — idempotente. */
    @Transactional
    public BookingResponse confirmPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("CONFIRMED".equals(booking.getStatus())) {
            return toResponse(booking);
        }
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking not awaiting payment");
        }
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(Instant.now());
        bookingRepository.save(booking);
        messagingService.addMemberToWalkThread(booking.getWalkId(), booking.getUserId());
        return toResponse(booking);
    }

    /** R6 : libère les places PENDING_PAYMENT dont payment_expires_at est dépassé. */
    @Transactional
    public int expireUnpaidBookings() {
        Instant now = Instant.now();
        int count = 0;
        for (Booking booking : bookingRepository.findByStatusAndPaymentExpiresAtBefore("PENDING_PAYMENT", now)) {
            booking.setStatus("CANCELLED_FREE");
            booking.setCancelledAt(now);
            booking.setCancelledByRole("SYSTEM");
            booking.setCancellationReason("payment_expired");
            bookingRepository.save(booking);
            waitlistService.offerNext(booking.getWalkId());
            count++;
        }
        return count;
    }

    private BookingResponse toResponse(Booking b) {
        return bookingResponseFactory.from(b);
    }
}
