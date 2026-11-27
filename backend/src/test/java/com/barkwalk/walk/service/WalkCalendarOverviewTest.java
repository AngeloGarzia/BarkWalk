package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import com.barkwalk.walker.service.WalkerPublishEligibility;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalkCalendarOverviewTest {

    @Mock
    private WalkRepository walkRepository;
    @Mock
    private MeetingPointRepository meetingPointRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private PrivacyService privacyService;
    @Mock
    private DogRepository dogRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private WalkerPublishEligibility walkerPublishEligibility;
    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;
    @Mock
    private WalkRatingRepository walkRatingRepository;
    @Mock
    private WalkPhotoService walkPhotoService;
    @Mock
    private MessagingService messagingService;
    @Spy
    private WalkDifficultyCalculator difficultyCalculator = new WalkDifficultyCalculator();

    @InjectMocks
    private WalkService walkService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Autorise l'agenda pour un administrateur.
    @Test
    void calendarOverview_allowsAdmin() {
        SecurityTestSupport.authenticate(1L, "admin@barkwalk.fr", Role.ADMIN, Role.USER);
        when(walkRepository.findAdminOverview(isNull(), isNull())).thenReturn(List.of());
        assertThat(walkService.calendarOverview()).isEmpty();
    }

    // Autorise l'agenda pour un organisateur communautaire.
    @Test
    void calendarOverview_allowsCommunityOrg() {
        SecurityTestSupport.authenticate(2L, "org@barkwalk.fr", Role.USER, Role.MEMBER, Role.COMMUNITY_ORG);
        when(walkRepository.findAdminOverview(isNull(), isNull())).thenReturn(List.of());
        assertThat(walkService.calendarOverview()).isEmpty();
    }

    // Refuse l'agenda aux comptes sans rôle admin ou organisateur.
    @Test
    void calendarOverview_forbidsPlainMember() {
        SecurityTestSupport.authenticate(3L, "member@barkwalk.fr", Role.USER, Role.MEMBER);
        assertThatThrownBy(() -> walkService.calendarOverview())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
