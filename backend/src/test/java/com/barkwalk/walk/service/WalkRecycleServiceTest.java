package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.dto.WalkDtos.WalkRecycleRequest;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import com.barkwalk.walker.service.WalkerPublishEligibility;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalkRecycleServiceTest {

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

    @BeforeEach
    void stubs() {
        org.mockito.Mockito.lenient()
                .when(privacyService.displayName(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("BergesCamille");
        org.mockito.Mockito.lenient()
                .when(walkPhotoService.promoUrls(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(walkPhotoService.galleryCount(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(0L);
        org.mockito.Mockito.lenient()
                .when(bookingRepository.countByWalkIdAndStatusIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(messagingService)
                .ensureWalkThread(org.mockito.ArgumentMatchers.any());
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Recycle copie les attributs et relie à la source.
    @Test
    void recycle_copiesAttributesAndLinksSource() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk source = sourceWalk();
        when(walkRepository.findById(21L)).thenReturn(Optional.of(source));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 99L);
            return w;
        });
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.empty());

        Instant future = Instant.now().plus(5, ChronoUnit.DAYS);
        var response = walkService.recycle(21L, new WalkRecycleRequest(future));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.recycledFromWalkId()).isEqualTo(21L);
        assertThat(response.title()).isEqualTo(source.getTitle());
        assertThat(response.organizerUserId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.difficultyLevel()).isEqualTo(4);
    }

    // Refuse de recycler une source future encore publiée.
    @Test
    void recycle_rejectsFutureSourceStillPublished() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk source = sourceWalk();
        source.setStatus("PUBLISHED");
        source.setArchivedAt(null);
        when(walkRepository.findById(21L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> walkService.recycle(21L, new WalkRecycleRequest(Instant.now().plus(2, ChronoUnit.DAYS))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Walk sourceWalk() {
        Walk walk = new Walk();
        setId(walk, 21L);
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        walk.setWalkType("COMMUNITY");
        walk.setTitle("Descente Croix-Rousse");
        walk.setDescription("Pentes");
        walk.setStartsAt(Instant.now().minus(3, ChronoUnit.DAYS));
        walk.setDurationMinutes(50);
        walk.setElevationGainM(90);
        walk.setTerrainType("TRAIL");
        walk.setDifficultyLevel(4);
        walk.setCapacity(6);
        walk.setOrganizerUserId(7L);
        walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM"});
        walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM"});
        walk.setStatus("COMPLETED");
        walk.setArchivedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        return walk;
    }

    private MeetingPoint meetingPoint() {
        MeetingPoint mp = new MeetingPoint();
        setId(mp, 3L);
        mp.setCityId(1L);
        mp.setName("Croix-Rousse");
        mp.setAddress("Lyon");
        mp.setLat(45.77);
        mp.setLng(4.83);
        return mp;
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
