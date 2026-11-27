package com.barkwalk.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.Role;
import com.barkwalk.review.domain.WalkRating;
import com.barkwalk.review.dto.ReviewDtos.WalkRatingRequest;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
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
class WalkRatingServiceTest {

    @Mock
    private WalkRatingRepository walkRatingRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;

    @InjectMocks
    private WalkRatingService walkRatingService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Refuse l'évaluation avant le lendemain.
    @Test
    void rate_rejectsBeforeNextDay() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        Walk walk = walk(Instant.now().minus(2, ChronoUnit.HOURS), 60);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(booking(1L, "CONFIRMED")));

        assertThatThrownBy(() -> walkRatingService.rate(new WalkRatingRequest(10L, 5, "super")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("day after");
                });
    }

    // Accepte l'évaluation le lendemain de la balade.
    @Test
    void rate_okTheDayAfter() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        Walk walk = walk(Instant.now().minus(2, ChronoUnit.DAYS), 60);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(booking(1L, "CONFIRMED")));
        when(walkRatingRepository.existsByWalkIdAndUserId(10L, 1L)).thenReturn(false);
        when(businessSettingsService.reviewWindowDays()).thenReturn(14);
        when(walkRatingRepository.save(any(WalkRating.class))).thenAnswer(inv -> {
            WalkRating r = inv.getArgument(0);
            setId(r, 99L);
            return r;
        });
        when(walkerProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(walkRatingRepository.averageForCreator(7L, null)).thenReturn(4.0);
        when(walkRatingRepository.countForCreator(7L, null)).thenReturn(1L);
        Profile creator = new Profile();
        creator.setUserId(7L);
        when(profileRepository.findById(7L)).thenReturn(Optional.of(creator));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = walkRatingService.rate(new WalkRatingRequest(10L, 4, "Chouette balade"));
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.walkId()).isEqualTo(10L);
        assertThat(creator.getAverageRating()).isEqualByComparingTo("4.00");
        assertThat(creator.getRatingsCount()).isEqualTo(1);
    }

    // Refuse l'évaluation par un non-participant.
    @Test
    void rate_rejectsNonParticipant() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        Walk walk = walk(Instant.now().minus(2, ChronoUnit.DAYS), 45);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(booking(2L, "CONFIRMED")));

        assertThatThrownBy(() -> walkRatingService.rate(new WalkRatingRequest(10L, 3, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static Walk walk(Instant startsAt, int durationMinutes) {
        Walk walk = new Walk();
        setId(walk, 10L);
        walk.setTitle("Balade test");
        walk.setStartsAt(startsAt);
        walk.setDurationMinutes(durationMinutes);
        walk.setOrganizerUserId(7L);
        return walk;
    }

    private static Booking booking(Long userId, String status) {
        Booking b = new Booking();
        b.setUserId(userId);
        b.setWalkId(10L);
        b.setStatus(status);
        return b;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
