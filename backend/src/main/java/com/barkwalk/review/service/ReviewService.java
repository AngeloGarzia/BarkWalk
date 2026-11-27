package com.barkwalk.review.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.review.domain.Review;
import com.barkwalk.review.dto.ReviewDtos.ReviewRequest;
import com.barkwalk.review.dto.ReviewDtos.ReviewResponse;
import com.barkwalk.review.repository.ReviewRepository;
import com.barkwalk.user.service.CommunityOrgService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final WalkRepository walkRepository;
    private final BusinessSettingsService businessSettingsService;
    private final PrivacyService privacyService;
    private final CommunityOrgService communityOrgService;

    public ReviewService(
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            WalkRepository walkRepository,
            BusinessSettingsService businessSettingsService,
            PrivacyService privacyService,
            CommunityOrgService communityOrgService) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.walkRepository = walkRepository;
        this.businessSettingsService = businessSettingsService;
        this.privacyService = privacyService;
        this.communityOrgService = communityOrgService;
    }

    @Transactional
    public ReviewResponse create(ReviewRequest request) {
        Long authorId = SecurityUtils.currentUserId();
        if (request.targetUserId() == null || authorId.equals(request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot review yourself");
        }
        if (reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(
                request.walkId(), authorId, request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }
        Walk walk = walkRepository
                .findById(request.walkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int days = businessSettingsService.reviewWindowDays();
        if (walk.getStartsAt().plus(days, ChronoUnit.DAYS).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review window closed");
        }
        // R13 : les deux doivent avoir une réservation TERMINÉE sur la même balade.
        boolean authorOk = bookingRepository.findByWalkId(request.walkId()).stream()
                .anyMatch(b -> b.getUserId().equals(authorId) && "COMPLETED".equals(b.getStatus()));
        boolean targetOk = bookingRepository.findByWalkId(request.walkId()).stream()
                .anyMatch(b -> b.getUserId().equals(request.targetUserId()) && "COMPLETED".equals(b.getStatus()));
        if (!authorOk || !targetOk) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both users must have completed the walk");
        }
        Review review = new Review();
        review.setWalkId(request.walkId());
        review.setAuthorId(authorId);
        review.setTargetUserId(request.targetUserId());
        review.setRating(request.rating());
        review.setComment(request.comment());
        Review saved = reviewRepository.save(review);
        communityOrgService.evaluateRevocation(request.targetUserId());
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse reply(Long reviewId, String reply) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!review.getTargetUserId().equals(SecurityUtils.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the rated user can reply");
        }
        if (reply == null || reply.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply required");
        }
        review.setReply(reply.trim());
        return toResponse(review);
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getWalkId(),
                r.getAuthorId(),
                privacyService.displayName(r.getAuthorId()),
                r.getTargetUserId(),
                privacyService.displayName(r.getTargetUserId()),
                r.getRating(),
                r.getComment(),
                r.getReply(),
                r.getCreatedAt());
    }
}
