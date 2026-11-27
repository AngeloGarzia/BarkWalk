package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StoredImage;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.domain.WalkPhoto;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkPhotoResponse;
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

    // Refuse l'upload de galerie avant le démarrage.
    @Test
    void upload_gallery_rejectsBeforeStart() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk walk = walk(1L, 7L, "PUBLISHED");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));

        MockMultipartFile file = jpeg();

        assertThatThrownBy(() -> walkPhotoService.upload(1L, "GALLERY", file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus((ResponseStatusException) ex, HttpStatus.BAD_REQUEST));
    }

    // Refuse l'upload promo par un non-créateur.
    @Test
    void upload_promo_rejectsNonCreator() {
        SecurityTestSupport.authenticate(5L, "user@barkwalk.fr", Role.USER);
        Walk walk = walk(2L, 7L, "PUBLISHED");
        when(walkRepository.findById(2L)).thenReturn(Optional.of(walk));

        assertThatThrownBy(() -> walkPhotoService.upload(2L, "PROMO", jpeg()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus((ResponseStatusException) ex, HttpStatus.FORBIDDEN));
    }

    // Accepte un upload promo par le créateur.
    @Test
    void upload_promo_success() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk walk = walk(1L, 7L, "PUBLISHED");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));
        when(walkPhotoRepository.countByWalkIdAndKind(1L, "PROMO")).thenReturn(0L);
        when(storageService.storeImage(anyString(), any())).thenReturn(new StoredImage("walks/1/promo/a.jpg", "image/jpeg"));
        when(storageService.publicMediaUrl("walks/1/promo/a.jpg")).thenReturn("/api/media/walks/1/promo/a.jpg");
        when(privacyService.displayName(7L)).thenReturn("Org");
        when(walkPhotoRepository.save(any(WalkPhoto.class))).thenAnswer(inv -> {
            WalkPhoto p = inv.getArgument(0);
            setId(p, 99L);
            return p;
        });

        WalkPhotoResponse response = walkPhotoService.upload(1L, "PROMO", jpeg());
        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.kind()).isEqualTo("PROMO");
        assertThat(response.url()).contains("promo");
        assertThat(response.uploaderDisplayName()).isEqualTo("Org");
    }

    // Refuse si le quota promo est atteint.
    @Test
    void upload_promo_rejectsWhenFull() {
        SecurityTestSupport.authenticate(7L, "org@barkwalk.fr", Role.USER, Role.COMMUNITY_ORG);
        Walk walk = walk(1L, 7L, "PUBLISHED");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));
        when(walkPhotoRepository.countByWalkIdAndKind(1L, "PROMO")).thenReturn(6L);

        assertThatThrownBy(() -> walkPhotoService.upload(1L, "PROMO", jpeg()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Maximum");
                });
    }

    // Liste les photos d'une balade filtrées par kind.
    @Test
    void list_filtersByKind() {
        WalkPhoto photo = photo(11L, 1L, 7L, "GALLERY");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk(1L, 7L, "COMPLETED")));
        when(walkPhotoRepository.findByWalkIdAndKindOrderBySortOrderAsc(1L, "GALLERY")).thenReturn(List.of(photo));
        when(storageService.publicMediaUrl(photo.getStorageKey())).thenReturn("/m");
        when(privacyService.displayName(7L)).thenReturn("Org");

        List<WalkPhotoResponse> list = walkPhotoService.list(1L, "GALLERY");
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().id()).isEqualTo(11L);
    }

    // Supprime une photo en tant qu'uploader.
    @Test
    void delete_byUploader() {
        SecurityTestSupport.authenticate(7L, "u@barkwalk.fr", Role.USER);
        Walk walk = walk(1L, 9L, "COMPLETED");
        WalkPhoto photo = photo(11L, 1L, 7L, "GALLERY");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));
        when(walkPhotoRepository.findByIdAndWalkId(11L, 1L)).thenReturn(Optional.of(photo));

        walkPhotoService.delete(1L, 11L);
        verify(walkPhotoRepository).delete(photo);
        verify(storageService).deleteObject(photo.getStorageKey());
    }

    // Agrège les photos de plusieurs balades et compte la galerie.
    @Test
    void listForWalks_and_counts() {
        when(walkPhotoRepository.findByWalkIdInOrderByWalkIdAscKindAscSortOrderAsc(List.of(1L, 2L)))
                .thenReturn(List.of(photo(1L, 1L, 7L, "PROMO")));
        when(storageService.publicMediaUrl(anyString())).thenReturn("/m");
        when(privacyService.displayName(7L)).thenReturn("Org");
        when(walkPhotoRepository.findByWalkIdAndKindOrderBySortOrderAsc(1L, "PROMO"))
                .thenReturn(List.of(photo(1L, 1L, 7L, "PROMO")));
        when(walkPhotoRepository.countByWalkIdAndKind(1L, "GALLERY")).thenReturn(3L);

        assertThat(walkPhotoService.listForWalks(List.of())).isEmpty();
        assertThat(walkPhotoService.listForWalks(List.of(1L, 2L))).hasSize(1);
        assertThat(walkPhotoService.promoUrls(1L, 2)).hasSize(1);
        assertThat(walkPhotoService.galleryCount(1L)).isEqualTo(3L);
    }

    private static Walk walk(long id, long organizerId, String status) {
        Walk walk = new Walk();
        setId(walk, id);
        walk.setOrganizerUserId(organizerId);
        walk.setStatus(status);
        return walk;
    }

    private static WalkPhoto photo(long id, long walkId, long uploader, String kind) {
        WalkPhoto photo = new WalkPhoto();
        setId(photo, id);
        photo.setWalkId(walkId);
        photo.setUploadedBy(uploader);
        photo.setStorageKey("walks/" + walkId + "/" + kind.toLowerCase() + "/" + id + ".jpg");
        photo.setContentType("image/jpeg");
        photo.setKind(kind);
        photo.setSortOrder((short) 0);
        return photo;
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
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
