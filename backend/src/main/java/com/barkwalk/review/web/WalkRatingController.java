package com.barkwalk.review.web;

import com.barkwalk.review.dto.ReviewDtos.WalkRatingRequest;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingResponse;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingSummary;
import com.barkwalk.review.service.WalkRatingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/walk-ratings")
public class WalkRatingController {
    private final WalkRatingService walkRatingService;

    public WalkRatingController(WalkRatingService walkRatingService) {
        this.walkRatingService = walkRatingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalkRatingResponse rate(@Valid @RequestBody WalkRatingRequest request) {
        return walkRatingService.rate(request);
    }

    @GetMapping("/mine")
    public List<WalkRatingResponse> mine() {
        return walkRatingService.mine();
    }

    @GetMapping("/rateable")
    public List<WalkRatingResponse> rateable() {
        return walkRatingService.rateableForMe();
    }

    @GetMapping("/walk/{walkId}/summary")
    public WalkRatingSummary summary(@PathVariable Long walkId) {
        return walkRatingService.summary(walkId);
    }
}
