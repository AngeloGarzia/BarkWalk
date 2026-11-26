package com.barkwalk.dog.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// R2 / R16 : validité dossier (vaccination + assurance RC) et expiration.
@Service
public class DogDossierService {
    public static final String DOC_VACCINATION = "VACCINATION";
    public static final String DOC_INSURANCE = "INSURANCE";
    public static final Set<String> REQUIRED_DOC_TYPES = Set.of(DOC_VACCINATION, DOC_INSURANCE);
    private static final Set<String> ACTIVE_BOOKING_STATUSES = Set.of("CONFIRMED", "PENDING_PAYMENT");

    private final DogRepository dogRepository;
    private final DogDocumentRepository documentRepository;
    private final BookingRepository bookingRepository;
    private final WalkRepository walkRepository;
    private final AuditService auditService;

    public DogDossierService(
            DogRepository dogRepository,
            DogDocumentRepository documentRepository,
            BookingRepository bookingRepository,
            WalkRepository walkRepository,
            AuditService auditService) {
        this.dogRepository = dogRepository;
        this.documentRepository = documentRepository;
        this.bookingRepository = bookingRepository;
        this.walkRepository = walkRepository;
        this.auditService = auditService;
    }

    /** R2 : inscription uniquement si dossier validé et documents requis non expirés. */
    @Transactional
    public void assertBookable(Dog dog) {
        refreshExpiry(dog);
        if ("EXPIRED".equals(dog.getDossierStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog dossier expired");
        }
        if (!"VALIDATED".equals(dog.getDossierStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog dossier not validated");
        }
        if (!hasValidRequiredDocuments(dog.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Dog dossier missing valid vaccination or insurance");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasValidRequiredDocuments(Long dogId) {
        List<DogDocument> docs = documentRepository.findByDogId(dogId);
        LocalDate today = LocalDate.now();
        return REQUIRED_DOC_TYPES.stream()
                .allMatch(type -> docs.stream().anyMatch(d -> type.equals(d.getDocType()) && isCurrentlyValid(d, today)));
    }

    @Transactional(readOnly = true)
    public void assertReadyForAdminValidation(Long dogId) {
        if (!hasValidRequiredDocuments(dogId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vaccination and insurance must be validated and not expired");
        }
    }

    /**
     * R16 : si un document requis passe sa date d'expiration → statut EXPIRED sur le doc et le dossier,
     * nouvelles résas bloquées, résas futures signalées admin.
     */
    @Transactional
    public void refreshExpiry(Dog dog) {
        if (!"VALIDATED".equals(dog.getDossierStatus()) && !"EXPIRED".equals(dog.getDossierStatus())) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean requiredDocExpired = false;
        for (DogDocument doc : documentRepository.findByDogId(dog.getId())) {
            if (!REQUIRED_DOC_TYPES.contains(doc.getDocType())) {
                continue;
            }
            if ("VALIDATED".equals(doc.getStatus())
                    && doc.getExpiresAt() != null
                    && doc.getExpiresAt().isBefore(today)) {
                doc.setStatus("EXPIRED");
                documentRepository.save(doc);
                requiredDocExpired = true;
            } else if ("EXPIRED".equals(doc.getStatus())) {
                requiredDocExpired = true;
            }
        }
        if (requiredDocExpired && !"EXPIRED".equals(dog.getDossierStatus())) {
            markDossierExpired(dog);
        }
    }

    @Transactional
    public int expireOverdueDossiers() {
        int count = 0;
        for (Dog dog : dogRepository.findByDossierStatus("VALIDATED")) {
            String before = dog.getDossierStatus();
            refreshExpiry(dog);
            if ("EXPIRED".equals(dog.getDossierStatus()) && !"EXPIRED".equals(before)) {
                count++;
            }
        }
        return count;
    }

    private void markDossierExpired(Dog dog) {
        dog.setDossierStatus("EXPIRED");
        dog.setUpdatedAt(Instant.now());
        dogRepository.save(dog);
        flagFutureBookings(dog.getId());
        Long actor = safeActorId();
        if (actor != null) {
            auditService.log(actor, "DOG_DOSSIER_EXPIRED", "DOG", dog.getId(), null);
        } else {
            auditService.log(null, "DOG_DOSSIER_EXPIRED", "DOG", dog.getId(), "scheduled");
        }
    }

    private void flagFutureBookings(Long dogId) {
        Instant now = Instant.now();
        for (Booking booking : bookingRepository.findByDogId(dogId)) {
            if (!ACTIVE_BOOKING_STATUSES.contains(booking.getStatus()) || booking.isAdminAlert()) {
                continue;
            }
            Walk walk = walkRepository.findById(booking.getWalkId()).orElse(null);
            if (walk == null || walk.getStartsAt() == null || !walk.getStartsAt().isAfter(now)) {
                continue;
            }
            booking.setAdminAlert(true);
            bookingRepository.save(booking);
        }
    }

    private boolean isCurrentlyValid(DogDocument doc, LocalDate today) {
        if (!"VALIDATED".equals(doc.getStatus())) {
            return false;
        }
        return doc.getExpiresAt() == null || !doc.getExpiresAt().isBefore(today);
    }

    private Long safeActorId() {
        try {
            return SecurityUtils.currentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
