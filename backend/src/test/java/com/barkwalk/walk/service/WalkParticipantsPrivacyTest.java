package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
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
class WalkParticipantsPrivacyTest {

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

    @InjectMocks
    private WalkService walkService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Interdit l'accès aux participants pour un simple membre.
    @Test
    void participants_forbiddenForSimpleMember() {
        SecurityTestSupport.authenticate(10L, "member@example.com", Role.USER, Role.MEMBER);
        Walk walk = new Walk();
        setId(walk, 1L);
        walk.setWalkerId(99L);
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));
        when(walkerProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walkService.participants(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // Autorise l'accès pour le marcheur assigné et inclut téléphones.
    @Test
    void participants_okForAssignedWalker_includesPhone() {
        SecurityTestSupport.authenticate(5L, "walker@example.com", Role.USER, Role.WALKER_STAFF);
        Walk walk = new Walk();
        setId(walk, 1L);
        walk.setWalkerId(7L);
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));

        WalkerProfile wp = new WalkerProfile();
        setId(wp, 7L);
        wp.setUserId(5L);
        when(walkerProfileRepository.findByUserId(5L)).thenReturn(Optional.of(wp));

        Booking booking = new Booking();
        setId(booking, 50L);
        booking.setWalkId(1L);
        booking.setUserId(20L);
        booking.setDogId(30L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findByWalkId(1L)).thenReturn(List.of(booking));

        Dog dog = new Dog();
        setId(dog, 30L);
        dog.setName("Pixel");
        when(dogRepository.findById(30L)).thenReturn(Optional.of(dog));
        when(privacyService.displayName(20L)).thenReturn("Camille D.");
        when(privacyService.phoneForViewer(5L, 20L, 1L)).thenReturn("+33611111111");

        var response = walkService.participants(1L);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().getFirst().phone()).isEqualTo("+33611111111");
        assertThat(response.participants().getFirst().displayName()).isEqualTo("Camille D.");
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
