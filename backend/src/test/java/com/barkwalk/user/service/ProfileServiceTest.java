package com.barkwalk.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StoredImage;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.ProfilePhoto;
import com.barkwalk.user.repository.ProfilePhotoRepository;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfilePhotoRepository photoRepository;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProfileService profileService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(1L, "alice@example.com", Role.USER);
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void uploadPhoto_storesAndReturnsSignedUrl() {
        Profile profile = profile(1L);
        when(profileRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(photoRepository.countByUserId(1L)).thenReturn(0L);
        when(storageService.storeImage(eq("profiles/1"), any()))
                .thenReturn(new StoredImage("profiles/1/a.jpg", "image/jpeg"));
        ProfilePhoto[] saved = new ProfilePhoto[1];
        when(photoRepository.save(any(ProfilePhoto.class))).thenAnswer(inv -> {
            ProfilePhoto p = inv.getArgument(0);
            setId(p, 10L);
            saved[0] = p;
            return p;
        });
        when(photoRepository.findByUserIdOrderBySortOrderAsc(1L))
                .thenReturn(List.of())
                .thenAnswer(inv -> List.of(saved[0]));
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(storageService.publicMediaUrl("profiles/1/a.jpg")).thenReturn("/api/media/profiles/1/a.jpg?exp=1&sig=x");

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/png", new byte[] {1, 2, 3});
        var response = profileService.uploadPhoto(file);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sortOrder()).isEqualTo((short) 0);
        assertThat(response.url()).contains("/api/media/");
        assertThat(profile.getPhotoKey()).isEqualTo("profiles/1/a.jpg");
    }

    @Test
    void uploadPhoto_rejectsFourthPhoto() {
        when(profileRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(profile(1L)));
        when(photoRepository.countByUserId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> profileService.uploadPhoto(
                        new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1})))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(storageService, never()).storeImage(anyString(), any());
    }

    @Test
    void deletePhoto_reindexesAndSyncsLegacyKey() {
        Profile profile = profile(1L);
        profile.setPhotoKey("profiles/1/old.jpg");
        ProfilePhoto first = photo(1L, 1L, "profiles/1/a.jpg", (short) 0);
        ProfilePhoto second = photo(2L, 1L, "profiles/1/b.jpg", (short) 1);

        when(profileRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(photoRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(first));
        when(photoRepository.findByUserIdOrderBySortOrderAsc(1L))
                .thenReturn(new ArrayList<>(List.of(second)))
                .thenReturn(List.of(second));
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        profileService.deletePhoto(1L);

        ArgumentCaptor<List<ProfilePhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getSortOrder()).isEqualTo((short) 0);
        assertThat(profile.getPhotoKey()).isEqualTo("profiles/1/b.jpg");
        verify(photoRepository).delete(first);
    }

    private static Profile profile(long userId) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setFirstName("A");
        p.setLastName("B");
        return p;
    }

    private static ProfilePhoto photo(long id, long userId, String key, short order) {
        ProfilePhoto p = new ProfilePhoto();
        setId(p, id);
        p.setUserId(userId);
        p.setStorageKey(key);
        p.setContentType("image/jpeg");
        p.setSortOrder(order);
        return p;
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
