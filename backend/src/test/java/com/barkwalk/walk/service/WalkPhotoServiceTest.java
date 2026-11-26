package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.storage.StorageService;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkPhotoRepository;
import com.barkwalk.walk.repository.WalkRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalkPhotoServiceTest {

    @Mock
    private WalkRepository walkRepository;
    @Mock
    private WalkPhotoRepository walkPhotoRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private PrivacyService privacyService;

    @InjectMocks
    private WalkPhotoService walkPhotoService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void upload_gallery_rejectsBeforeStart() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk walk = new Walk();
        setId(walk, 1L);
        walk.setOrganizerUserId(7L);
        walk.setStatus("PUBLISHED");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));

        MockMultipartFile file =
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThatThrownBy(() -> walkPhotoService.upload(1L, "GALLERY", file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus((ResponseStatusException) ex, HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_promo_rejectsNonCreator() {
        SecurityTestSupport.authenticate(5L, "user@barkwalk.fr", Role.USER);
        Walk walk = new Walk();
        setId(walk, 2L);
        walk.setOrganizerUserId(7L);
        walk.setStatus("PUBLISHED");
        when(walkRepository.findById(2L)).thenReturn(Optional.of(walk));

        MockMultipartFile file =
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThatThrownBy(() -> walkPhotoService.upload(2L, "PROMO", file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus((ResponseStatusException) ex, HttpStatus.FORBIDDEN));
    }

    private static void assertThatStatus(ResponseStatusException ex, HttpStatus expected) {
        if (!ex.getStatusCode().equals(expected)) {
            throw new AssertionError("Expected " + expected + " but was " + ex.getStatusCode());
        }
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
