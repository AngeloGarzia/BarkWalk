package com.barkwalk.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.review.domain.Review;
import com.barkwalk.review.dto.ReviewDtos.ReviewRequest;
import com.barkwalk.review.repository.ReviewRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.service.CommunityOrgService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private PrivacyService privacyService;
    @Mock
    private CommunityOrgService communityOrgService;

    @InjectMocks
    private ReviewService reviewService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void create_rejectsOutsideWindow() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(10L, 1L, 2L)).thenReturn(false);
        Walk walk = new Walk();
        walk.setStartsAt(Instant.now().minus(20, ChronoUnit.DAYS));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(businessSettingsService.reviewWindowDays()).thenReturn(14);

        assertThatThrownBy(() -> reviewService.create(new ReviewRequest(10L, 2L, 5, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsWithoutCompletedBookings() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(10L, 1L, 2L)).thenReturn(false);
        Walk walk = new Walk();
        walk.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(businessSettingsService.reviewWindowDays()).thenReturn(14);
        Booking confirmed = new Booking();
        confirmed.setUserId(1L);
        confirmed.setStatus("CONFIRMED");
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(confirmed));

        assertThatThrownBy(() -> reviewService.create(new ReviewRequest(10L, 2L, 5, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void create_rejectsDuplicate() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(10L, 1L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> reviewService.create(new ReviewRequest(10L, 2L, 5, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void create_okWhenBothCompleted() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(reviewRepository.existsByWalkIdAndAuthorIdAndTargetUserId(10L, 1L, 2L)).thenReturn(false);
        Walk walk = new Walk();
        walk.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(businessSettingsService.reviewWindowDays()).thenReturn(14);
        Booking a = new Booking();
        a.setUserId(1L);
        a.setStatus("COMPLETED");
        Booking b = new Booking();
        b.setUserId(2L);
        b.setStatus("COMPLETED");
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(a, b));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            setId(r, 9L);
            return r;
        });
        when(privacyService.displayName(any())).thenReturn("X");

        var response = reviewService.create(new ReviewRequest(10L, 2L, 5, "super"));
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.rating()).isEqualTo(5);
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
