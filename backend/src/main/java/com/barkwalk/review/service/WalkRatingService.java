package com.barkwalk.review.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.review.domain.WalkRating;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingRequest;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingResponse;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingSummary;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Notation de balade par les participants : 1–5 ★, à partir du lendemain, dans la fenêtre d'avis
 * (R13, paramètre {@code review_window_days}). Met à jour la note moyenne du créateur.
 */
@Service
public class WalkRatingService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final WalkRatingRepository walkRatingRepository;
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final BusinessSettingsService businessSettingsService;
    private final ProfileRepository profileRepository;
    private final WalkerProfileRepository walkerProfileRepository;

    public WalkRatingService(
            WalkRatingRepository walkRatingRepository,
            WalkRepository walkRepository,
            BookingRepository bookingRepository,
            BusinessSettingsService businessSettingsService,
            ProfileRepository profileRepository,
            WalkerProfileRepository walkerProfileRepository) {
        this.walkRatingRepository = walkRatingRepository;
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.businessSettingsService = businessSettingsService;
        this.profileRepository = profileRepository;
        this.walkerProfileRepository = walkerProfileRepository;
    }

    @Transactional
    public WalkRatingResponse rate(WalkRatingRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Walk walk = walkRepository
                .findById(request.walkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Walk not found"));

        assertParticipant(walk.getId(), userId);
        assertRatingWindow(walk);

        if (walkRatingRepository.existsByWalkIdAndUserId(walk.getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Walk already rated");
        }

        WalkRating rating = new WalkRating();
        rating.setWalkId(walk.getId());
        rating.setUserId(userId);
        rating.setRating(request.rating());
        rating.setComment(trimComment(request.comment()));
        WalkRating saved = walkRatingRepository.save(rating);
        refreshCreatorAverage(walk);
        return toResponse(saved, walk, false);
    }

    @Transactional(readOnly = true)
    public List<WalkRatingResponse> mine() {
        Long userId = SecurityUtils.currentUserId();
        return walkRatingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> {
                    Walk walk = walkRepository.findById(r.getWalkId()).orElse(null);
                    return toResponse(r, walk, false);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public WalkRatingSummary summary(Long walkId) {
        Long userId = SecurityUtils.currentUserIdOrNull();
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Walk not found"));
        double avg = walkRatingRepository.averageForWalk(walkId);
        long count = walkRatingRepository.countByWalkId(walkId);
        Integer myRating = null;
        boolean canRate = false;
        if (userId != null) {
            myRating = walkRatingRepository
                    .findByWalkIdAndUserId(walkId, userId)
                    .map(WalkRating::getRating)
                    .orElse(null);
            canRate = myRating == null && isParticipant(walkId, userId) && isWithinRatingWindow(walk);
        }
        return new WalkRatingSummary(walkId, avg, count, myRating, canRate);
    }

    @Transactional(readOnly = true)
    public List<WalkRatingResponse> rateableForMe() {
        Long userId = SecurityUtils.currentUserId();
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(b -> "CONFIRMED".equals(b.getStatus()) || "COMPLETED".equals(b.getStatus()))
                .map(Booking::getWalkId)
                .distinct()
                .map(walkRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(walk -> isWithinRatingWindow(walk)
                        && !walkRatingRepository.existsByWalkIdAndUserId(walk.getId(), userId))
                .map(walk -> new WalkRatingResponse(
                        null,
                        walk.getId(),
                        walk.getTitle(),
                        walk.getStartsAt(),
                        userId,
                        0,
                        null,
                        null,
                        true))
                .toList();
    }

    /** Recalcule la moyenne du créateur (organisateur, sinon encadrant assigné). */
    void refreshCreatorAverage(Walk walk) {
        Long creatorUserId = resolveCreatorUserId(walk);
        if (creatorUserId == null) {
            return;
        }
        Long walkerId = walkerProfileRepository.findByUserId(creatorUserId).map(WalkerProfile::getId).orElse(null);
        double avg = walkRatingRepository.averageForCreator(creatorUserId, walkerId);
        long count = walkRatingRepository.countForCreator(creatorUserId, walkerId);
        BigDecimal rounded = count == 0
                ? null
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);

        profileRepository.findById(creatorUserId).ifPresent(profile -> {
            profile.setAverageRating(rounded);
            profile.setRatingsCount((int) count);
            profile.setUpdatedAt(Instant.now());
            profileRepository.save(profile);
        });

        if (walkerId != null) {
            walkerProfileRepository.findById(walkerId).ifPresent(wp -> {
                wp.setAverageRating(rounded);
                wp.setRatingsCount((int) count);
                walkerProfileRepository.save(wp);
            });
        }
    }

    private Long resolveCreatorUserId(Walk walk) {
        if (walk.getOrganizerUserId() != null) {
            return walk.getOrganizerUserId();
        }
        if (walk.getWalkerId() != null) {
            return walkerProfileRepository.findById(walk.getWalkerId()).map(WalkerProfile::getUserId).orElse(null);
        }
        return null;
    }

    private void assertParticipant(Long walkId, Long userId) {
        if (!isParticipant(walkId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only participants can rate this walk");
        }
    }

    private boolean isParticipant(Long walkId, Long userId) {
        return bookingRepository.findByWalkId(walkId).stream()
                .anyMatch(b -> b.getUserId().equals(userId)
                        && ("CONFIRMED".equals(b.getStatus()) || "COMPLETED".equals(b.getStatus())));
    }

    private void assertRatingWindow(Walk walk) {
        Instant now = Instant.now();
        Instant opensAt = ratingOpensAt(walk);
        Instant closesAt = ratingClosesAt(walk);
        if (now.isBefore(opensAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rating opens the day after the walk");
        }
        if (now.isAfter(closesAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review window closed");
        }
    }

    private boolean isWithinRatingWindow(Walk walk) {
        Instant now = Instant.now();
        return !now.isBefore(ratingOpensAt(walk)) && !now.isAfter(ratingClosesAt(walk));
    }

    /** Ouverture : minuit (Europe/Paris) du lendemain de la fin de balade. */
    Instant ratingOpensAt(Walk walk) {
        Instant end = walkEnd(walk);
        LocalDate nextDay = end.atZone(ZONE).toLocalDate().plusDays(1);
        return nextDay.atStartOfDay(ZONE).toInstant();
    }

    Instant ratingClosesAt(Walk walk) {
        int days = businessSettingsService.reviewWindowDays();
        return walk.getStartsAt().plus(days, ChronoUnit.DAYS);
    }

    private Instant walkEnd(Walk walk) {
        if (walk.getCompletedAt() != null) {
            return walk.getCompletedAt();
        }
        return walk.getStartsAt().plus(Duration.ofMinutes(Math.max(walk.getDurationMinutes(), 0)));
    }

    private static String trimComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private WalkRatingResponse toResponse(WalkRating rating, Walk walk, boolean canRate) {
        return new WalkRatingResponse(
                rating.getId(),
                rating.getWalkId(),
                walk != null ? walk.getTitle() : null,
                walk != null ? walk.getStartsAt() : null,
                rating.getUserId(),
                rating.getRating(),
                rating.getComment(),
                rating.getCreatedAt(),
                canRate);
    }
}
