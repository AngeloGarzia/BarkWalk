package com.barkwalk.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogPhoto;
import com.barkwalk.dog.repository.DogPhotoRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.storage.StorageService;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.ProfilePhoto;
import com.barkwalk.user.repository.ProfilePhotoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private ProfilePhotoRepository profilePhotoRepository;
    @Mock private DogRepository dogRepository;
    @Mock private DogPhotoRepository dogPhotoRepository;
    @Mock private StorageService storageService;
    @Mock private EntityManager entityManager;

    // Instanciation manuelle : le constructeur ne prend pas EntityManager (@PersistenceContext).
    private AccountDeletionService accountDeletionService;

    @BeforeEach
    void setUp() throws Exception {
        accountDeletionService = new AccountDeletionService(
                profilePhotoRepository, dogRepository, dogPhotoRepository, storageService);
        var f = AccountDeletionService.class.getDeclaredField("entityManager");
        f.setAccessible(true);
        f.set(accountDeletionService, entityManager);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clear();
    }

    /**
     * Chemin nominal : photos profil et photos chien collectées, anonymisation native lancée,
     * puis fichiers supprimés du stockage objet.
     */
    @Test
    void deleteMyAccount_deletesPhotosAndAnonymizes() {
        SecurityTestSupport.authenticate(1L, "user@example.com", Role.USER);

        ProfilePhoto profilePhoto = new ProfilePhoto();
        profilePhoto.setUserId(1L);
        profilePhoto.setStorageKey("profile/photo1.jpg");

        Dog dog = dog(7L, 1L);

        DogPhoto dogPhoto = new DogPhoto();
        dogPhoto.setDogId(7L);
        dogPhoto.setStorageKey("dog/rex1.jpg");

        Query query = mock(Query.class);
        when(profilePhotoRepository.findByUserIdOrderBySortOrderAsc(1L))
                .thenReturn(List.of(profilePhoto));
        when(dogRepository.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of(dog));
        when(dogPhotoRepository.findByDogIdOrderBySortOrderAsc(7L)).thenReturn(List.of(dogPhoto));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);

        accountDeletionService.deleteMyAccount();

        verify(profilePhotoRepository).deleteAll(List.of(profilePhoto));
        verify(dogPhotoRepository).deleteAll(List.of(dogPhoto));
        verify(storageService).deleteObject("profile/photo1.jpg");
        verify(storageService).deleteObject("dog/rex1.jpg");
    }

    /** Sans photos, l'anonymisation native est quand même lancée (sans suppression stockage). */
    @Test
    void deleteMyAccount_anonymizesEvenWithoutPhotos() {
        SecurityTestSupport.authenticate(1L, "user@example.com", Role.USER);

        Query query = mock(Query.class);
        when(profilePhotoRepository.findByUserIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(dogRepository.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);

        accountDeletionService.deleteMyAccount();

        verify(storageService, never()).deleteObject(anyString());
        verify(entityManager).createNativeQuery(anyString());
    }

    /** Lève UNAUTHORIZED si aucun utilisateur n'est authentifié. */
    @Test
    void deleteMyAccount_throwsUnauthorizedWhenNotAuthenticated() {
        assertThatThrownBy(() -> accountDeletionService.deleteMyAccount())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Dog dog(long id, long ownerId) {
        Dog dog = new Dog();
        try {
            var f = Dog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(dog, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        dog.setOwnerId(ownerId);
        dog.setName("Rex");
        dog.setBreed("Labrador");
        dog.setSex("M");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");
        dog.setDossierStatus("VALIDATED");
        return dog;
    }
}
