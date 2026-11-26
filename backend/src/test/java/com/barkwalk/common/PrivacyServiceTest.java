package com.barkwalk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.Walk;
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

@ExtendWith(MockitoExtension.class)
class PrivacyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;

    @InjectMocks
    private PrivacyService privacyService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void memberCannotSeeAnotherMembersPhoneWithoutWalkRole() {
        SecurityTestSupport.authenticate(10L, "member@example.com", Role.USER, Role.MEMBER);
        assertThat(privacyService.canViewPhone(10L, 20L, null)).isFalse();
        assertThat(privacyService.phoneForViewer(10L, 20L, null)).isNull();
    }

    @Test
    void walkerSeesPhoneWhenTargetBookedOnTheirWalk() {
        SecurityTestSupport.authenticate(5L, "walker@example.com", Role.USER, Role.WALKER_STAFF);

        Walk walk = new Walk();
        setId(walk, 100L);
        walk.setWalkerId(7L);
        when(walkRepository.findById(100L)).thenReturn(Optional.of(walk));

        WalkerProfile wp = new WalkerProfile();
        setId(wp, 7L);
        wp.setUserId(5L);
        when(walkerProfileRepository.findByUserId(5L)).thenReturn(Optional.of(wp));

        Booking booking = new Booking();
        booking.setWalkId(100L);
        booking.setUserId(20L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findByWalkId(100L)).thenReturn(List.of(booking));

        Profile profile = new Profile();
        profile.setPhone("+33600000020");
        when(profileRepository.findById(20L)).thenReturn(Optional.of(profile));

        assertThat(privacyService.canViewPhone(5L, 20L, 100L)).isTrue();
        assertThat(privacyService.phoneForViewer(5L, 20L, 100L)).isEqualTo("+33600000020");
    }

    @Test
    void displayName_anonymizedWhenDeleted() {
        User user = new User();
        setId(user, 3L);
        user.setDeleted(true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        assertThat(privacyService.displayName(3L)).isEqualTo(PrivacyService.ANONYMIZED_DISPLAY_NAME);
    }

    @Test
    void displayName_usesProfileWhenActive() {
        User user = new User();
        setId(user, 4L);
        user.setDeleted(false);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        Profile profile = new Profile();
        profile.setFirstName("Alice");
        profile.setLastName("Martin");
        when(profileRepository.findById(4L)).thenReturn(Optional.of(profile));
        assertThat(privacyService.displayName(4L)).isEqualTo("Alice Martin");
    }

    @Test
    void displayName_prefersPseudo() {
        User user = new User();
        setId(user, 5L);
        user.setDeleted(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        Profile profile = new Profile();
        profile.setFirstName("Alice");
        profile.setLastName("Martin");
        profile.setPseudo("AliceCanine");
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        assertThat(privacyService.displayName(5L)).isEqualTo("AliceCanine");
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
