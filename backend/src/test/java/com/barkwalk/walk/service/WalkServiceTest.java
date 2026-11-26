package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.Role;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import java.time.Instant;
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
class WalkServiceTest {

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

    @InjectMocks
    private WalkService walkService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void catalogue_delegates() {
        when(walkRepository.catalogue(1L, null, Instant.EPOCH)).thenReturn(List.of());
        assertThat(walkService.catalogue(1L, null, Instant.EPOCH)).isEmpty();
        verify(walkRepository).catalogue(1L, null, Instant.EPOCH);
    }

    @Test
    void get_notFound() {
        when(walkRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> walkService.get(9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_staff_requiresStaffRole() {
        SecurityTestSupport.authenticate(1L, "user@example.com", Role.USER);
        var req = createRequest("STAFF_GUIDED");
        assertThatThrownBy(() -> walkService.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_staff_okForWalker() {
        SecurityTestSupport.authenticate(2L, "staff@example.com", Role.USER, Role.WALKER_STAFF);
        when(meetingPointRepository.existsById(3L)).thenReturn(true);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 50L);
            return w;
        });
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.create(createRequest("STAFF_GUIDED"));
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.walkType()).isEqualTo("STAFF_GUIDED");
    }

    @Test
    void create_community_forbiddenWhenNotEligible() {
        SecurityTestSupport.authenticate(4L, "member@example.com", Role.USER, Role.MEMBER);
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("NONE");
        profile.setCompletedWalksCount(0);
        when(profileRepository.findById(4L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);

        assertThatThrownBy(() -> walkService.create(createRequest("COMMUNITY")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_community_okWhenActiveOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("ACTIVE");
        profile.setCompletedWalksCount(10);
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);
        when(meetingPointRepository.existsById(3L)).thenReturn(true);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 77L);
            return w;
        });
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.create(createRequest("COMMUNITY"));
        assertThat(response.organizerUserId()).isEqualTo(5L);
        assertThat(response.id()).isEqualTo(77L);
    }

    private static WalkCreateRequest createRequest(String type) {
        return new WalkCreateRequest(
                1L,
                3L,
                type,
                "Balade test",
                "desc",
                Instant.now().plusSeconds(86400),
                60,
                6,
                0,
                1500,
                10L,
                List.of("LARGE"),
                List.of("HIGH"),
                "GOOD",
                null,
                true);
    }

    private static MeetingPoint meetingPoint() {
        MeetingPoint mp = new MeetingPoint();
        setId(mp, 3L);
        mp.setName("Parc");
        mp.setAddress("1 rue");
        mp.setLat(45.7);
        mp.setLng(4.8);
        return mp;
    }

    private static City city() {
        City city = new City();
        setId(city, 1L);
        try {
            var name = City.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(city, "Lyon");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return city;
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
