package com.barkwalk.walker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.storage.StorageService;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.dto.WalkerDtos.ApplyRequest;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalkerServiceTest {

    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private MeetingPointRepository meetingPointRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private WalkerService walkerService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(1L, "w@example.com", Role.USER);
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void apply_rejectsDuplicate() {
        when(walkerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(new WalkerProfile()));
        assertThatThrownBy(() -> walkerService.apply(new ApplyRequest("STAFF", null, null, null, "bio")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void apply_storesKeysServerSideAndHidesThem() {
        when(walkerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(storageService.createObjectKey(any(), any())).thenReturn("walkers/1/diploma/file.pdf");
        when(walkerProfileRepository.save(any(WalkerProfile.class))).thenAnswer(inv -> {
            WalkerProfile p = inv.getArgument(0);
            try {
                var f = WalkerProfile.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(p, 9L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return p;
        });
        var response = walkerService.apply(new ApplyRequest("STAFF", "123", "dip.pdf", null, "hello"));
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.hasDiploma()).isTrue();
        assertThat(response.hasRcPro()).isFalse();
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void pending_mapsWithoutSecrets() {
        WalkerProfile profile = new WalkerProfile();
        profile.setUserId(2L);
        profile.setWalkerType("INDEP");
        profile.setStatus("PENDING");
        profile.setSiret("SECRET");
        profile.setDiplomaKey("secret-key");
        when(walkerProfileRepository.findByStatus("PENDING")).thenReturn(java.util.List.of(profile));
        var list = walkerService.pending();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().hasDiploma()).isTrue();
        assertThat(list.getFirst().walkerType()).isEqualTo("INDEP");
    }
}
