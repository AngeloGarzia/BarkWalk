package com.barkwalk.review.web;

import com.barkwalk.review.dto.ReviewDtos.ReviewRequest;
import com.barkwalk.review.dto.ReviewDtos.ReviewResponse;
import com.barkwalk.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse create(@Valid @RequestBody ReviewRequest request) {
        return reviewService.create(request);
    }

    public record ReplyBody(String reply) {}

    @PostMapping("/{id}/reply")
    public ReviewResponse reply(@PathVariable Long id, @RequestBody ReplyBody body) {
        return reviewService.reply(id, body != null ? body.reply() : null);
    }
}
