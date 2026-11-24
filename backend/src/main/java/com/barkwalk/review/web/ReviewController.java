package com.barkwalk.review.web;

import com.barkwalk.admin.repository.SettingRepository;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.review.domain.Review;
import com.barkwalk.review.repository.ReviewRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final WalkRepository walkRepository;
    private final SettingRepository settingRepository;

    public ReviewController(ReviewRepository reviewRepository, BookingRepository bookingRepository,
                            WalkRepository walkRepository, SettingRepository settingRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.walkRepository = walkRepository;
        this.settingRepository = settingRepository;
    }

    public record ReviewRequest(@NotNull Long walkId, @NotNull Long targetUserId,
                                @Min(1) @Max(5) int rating, String comment) {}

    @PostMapping
    @Transactional
    public Review create(@RequestBody ReviewRequest request) {
        Long authorId = SecurityUtils.currentUserId();
        if (reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(request.walkId(), authorId, request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already reviewed");
        }
        Walk walk = walkRepository.findById(request.walkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int days = Integer.parseInt(settingRepository.findById("review_window_days").map(s -> s.getValue()).orElse("14"));
        if (walk.getStartsAt().plus(days, ChronoUnit.DAYS).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review window closed");
        }
        boolean authorOk = bookingRepository.findByWalkId(request.walkId()).stream()
                .anyMatch(b -> b.getUserId().equals(authorId) && ("COMPLETED".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus())));
        boolean targetOk = bookingRepository.findByWalkId(request.walkId()).stream()
                .anyMatch(b -> b.getUserId().equals(request.targetUserId()));
        if (!authorOk || !targetOk) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both users must have participated");
        }
        Review review = new Review();
        review.setWalkId(request.walkId());
        review.setAuthorId(authorId);
        review.setTargetUserId(request.targetUserId());
        review.setRating(request.rating());
        review.setComment(request.comment());
        return reviewRepository.save(review);
    }
}
