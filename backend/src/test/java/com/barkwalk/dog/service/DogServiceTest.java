package com.barkwalk.dog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.domain.DogPhoto;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogPhotoRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.dog.dto.DogDtos.DogRequest;
import com.barkwalk.dog.dto.DogDtos.DocumentRequest;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StoredImage;
import com.barkwalk.support.SecurityTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class DogServiceTest {

    @Mock
    private DogRepository dogRepository;
    @Mock
    private DogDocumentRepository documentRepository;
    @Mock
    private DogPhotoRepository photoRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditService auditService;
    @Mock
    private DogDossierService dogDossierService;

    @InjectMocks
    private DogService dogService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(1L, "owner@example.com", Role.USER);
        org.mockito.Mockito.lenient()
                .when(photoRepository.findByDogIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(dogDossierService)
                .assertReadyForAdminValidation(org.mockito.ArgumentMatchers.anyLong());
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void create_setsDraftAndOwner() {
        when(dogRepository.save(any(Dog.class))).thenAnswer(inv -> {
            Dog d = inv.getArgument(0);
            setId(d, 11L);
            return d;
        });
        var response = dogService.create(sampleRequest());
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.dossierStatus()).isEqualTo("DRAFT");
        assertThat(response.name()).isEqualTo("Rex");
    }

    @Test
    void update_forbiddenForForeignDog() {
        Dog dog = ownedDog(5L, 99L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> dogService.update(5L, sampleRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submit_setsPending() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        var response = dogService.submit(5L);
        assertThat(response.dossierStatus()).isEqualTo("PENDING");
    }

    @Test
    void update_validatedSensitiveField_setsPendingAndAudits() {
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("VALIDATED");
        dog.setBreed("Berger");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");
        dog.setSex("MALE");
        dog.setSterilized(true);
        dog.setBirthDate(LocalDate.of(2020, 1, 1));
        dog.setIdentificationNumber("123");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        DogRequest changed = new DogRequest(
                "Rex",
                "Berger",
                LocalDate.of(2020, 1, 1),
                "MALE",
                true,
                new BigDecimal("28.5"),
                "MEDIUM", // taille sensible
                "123",
                "HIGH",
                "GOOD",
                "GOOD",
                "GOOD",
                "LOW",
                null,
                null);

        var response = dogService.update(5L, changed);

        assertThat(response.dossierStatus()).isEqualTo("PENDING");
        assertThat(response.sizeCategory()).isEqualTo("MEDIUM");
        verify(auditService)
                .log(eq(1L), eq("DOG_SENSITIVE_EDIT"), eq("DOG"), eq(5L), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void update_validatedFreeFieldOnly_keepsValidated() {
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("VALIDATED");
        dog.setBreed("Berger");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");
        dog.setSex("MALE");
        dog.setSterilized(true);
        dog.setBirthDate(LocalDate.of(2020, 1, 1));
        dog.setIdentificationNumber("123");
        dog.setWeightKg(new BigDecimal("28.5"));
        dog.setRecallLevel("GOOD");
        dog.setLeashPulling("LOW");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        DogRequest freeOnly = new DogRequest(
                "Rexou", // name libre
                "Berger",
                LocalDate.of(2020, 1, 1),
                "MALE",
                true,
                new BigDecimal("30.0"), // poids libre
                "LARGE",
                "123",
                "HIGH",
                "GOOD",
                "GOOD",
                "GOOD",
                "LOW",
                "Calme en laisse", // notes libres
                null);

        var response = dogService.update(5L, freeOnly);

        assertThat(response.dossierStatus()).isEqualTo("VALIDATED");
        assertThat(response.name()).isEqualTo("Rexou");
    }

    @Test
    void update_draftAnyField_staysDraft() {
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("DRAFT");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        var response = dogService.update(5L, sampleRequest());

        assertThat(response.dossierStatus()).isEqualTo("DRAFT");
    }

    @Test
    void validate_requiresAdmin() {
        assertThatThrownBy(() -> dogService.validate(5L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validate_asAdmin() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(2L, "admin@example.com", Role.USER, Role.ADMIN);
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("PENDING");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        var response = dogService.validate(5L);
        assertThat(response.dossierStatus()).isEqualTo("VALIDATED");
        verify(dogDossierService).assertReadyForAdminValidation(5L);
        verify(auditService).log(eq(2L), eq("DOG_VALIDATE"), eq("DOG"), eq(5L), eq(null));
    }

    @Test
    void validate_rejectsWithoutRequiredDocs() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(2L, "admin@example.com", Role.USER, Role.ADMIN);
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("PENDING");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vaccination and insurance must be validated and not expired"))
                .when(dogDossierService)
                .assertReadyForAdminValidation(5L);
        assertThatThrownBy(() -> dogService.validate(5L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refuse_asAdmin() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(2L, "admin@example.com", Role.USER, Role.ADMIN);
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        var response = dogService.refuse(5L, "papers");
        assertThat(response.dossierStatus()).isEqualTo("REFUSED");
        assertThat(response.refusalReason()).isEqualTo("papers");
    }

    @Test
    void addDocument_andList() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(documentRepository.save(any(DogDocument.class))).thenAnswer(inv -> {
            DogDocument d = inv.getArgument(0);
            setId(d, 20L);
            return d;
        });
        when(storageService.createObjectKey(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("dogs/5/uuid-vaccine.pdf");
        when(storageService.signedPutUrl("dogs/5/uuid-vaccine.pdf")).thenReturn("https://put");
        when(storageService.signedGetUrl("dogs/5/uuid-vaccine.pdf")).thenReturn("https://signed");
        var doc = dogService.addDocument(5L, new DocumentRequest("VACCINE", "vaccine.pdf", LocalDate.now().plusYears(1)));
        assertThat(doc.signedUrl()).isEqualTo("https://signed");
        assertThat(doc.uploadUrl()).isEqualTo("https://put");

        when(documentRepository.findByDogId(5L)).thenReturn(List.of());
        assertThat(dogService.documents(5L)).isEmpty();
    }

    @Test
    void myDogs_andDelete() {
        when(dogRepository.findByOwnerIdOrderByNameAsc(1L)).thenReturn(List.of());
        assertThat(dogService.myDogs()).isEmpty();
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        dogService.delete(5L);
        verify(dogRepository).delete(dog);
    }

    @Test
    void pending_lists() {
        when(dogRepository.findByDossierStatus("PENDING")).thenReturn(List.of());
        assertThat(dogService.pending()).isEmpty();
    }

    @Test
    void update_resetsDraftWhenNotValidated() {
        Dog dog = ownedDog(5L, 1L);
        dog.setDossierStatus("PENDING");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        var response = dogService.update(5L, sampleRequest());
        assertThat(response.dossierStatus()).isEqualTo("DRAFT");
        assertThat(response.breed()).isEqualTo("Berger");
    }

    @Test
    void documents_forbiddenForForeignOwner() {
        Dog dog = ownedDog(5L, 99L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> dogService.documents(5L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listPhotos_returnsOwnerPhotos() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        DogPhoto photo = dogPhoto(9L, 5L, "dogs/5/photos/a.jpg", (short) 0);
        when(photoRepository.findByDogIdOrderBySortOrderAsc(5L)).thenReturn(List.of(photo));
        when(storageService.publicMediaUrl("dogs/5/photos/a.jpg")).thenReturn("/api/media/x");
        assertThat(dogService.listPhotos(5L)).hasSize(1);
        assertThat(dogService.listPhotos(5L).getFirst().id()).isEqualTo(9L);
    }

    @Test
    void documents_listsSignedUrlsForOwner() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        DogDocument doc = new DogDocument();
        setId(doc, 3L);
        doc.setDogId(5L);
        doc.setDocType("VACCINE");
        doc.setStorageKey("dogs/5/v.pdf");
        doc.setStatus("PENDING");
        when(documentRepository.findByDogId(5L)).thenReturn(List.of(doc));
        when(storageService.signedGetUrl("dogs/5/v.pdf")).thenReturn("https://get");
        assertThat(dogService.documents(5L)).hasSize(1);
        assertThat(dogService.documents(5L).getFirst().signedUrl()).isEqualTo("https://get");
    }

    @Test
    void delete_removesPhotosAfterCommitPath() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(photoRepository.findByDogIdOrderBySortOrderAsc(5L))
                .thenReturn(List.of(dogPhoto(1L, 5L, "dogs/5/photos/a.jpg", (short) 0)));
        dogService.delete(5L);
        verify(photoRepository).deleteByDogId(5L);
        verify(dogRepository).delete(dog);
        verify(storageService).deleteObject("dogs/5/photos/a.jpg");
    }

    @Test
    void uploadPhoto_storesImageForOwner() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(dog));
        when(photoRepository.countByDogId(5L)).thenReturn(0L);
        when(storageService.storeImage(eq("dogs/5/photos"), any()))
                .thenReturn(new StoredImage("dogs/5/photos/a.jpg", "image/jpeg"));
        DogPhoto[] saved = new DogPhoto[1];
        when(photoRepository.save(any(DogPhoto.class))).thenAnswer(inv -> {
            DogPhoto p = inv.getArgument(0);
            setId(p, 40L);
            saved[0] = p;
            return p;
        });
        when(photoRepository.findByDogIdOrderBySortOrderAsc(5L))
                .thenReturn(List.of())
                .thenAnswer(inv -> List.of(saved[0]));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(storageService.publicMediaUrl("dogs/5/photos/a.jpg")).thenReturn("/api/media/dogs/5/photos/a.jpg?exp=1&sig=x");

        var response = dogService.uploadPhoto(
                5L, new MockMultipartFile("file", "a.jpg", "text/plain", new byte[] {1, 2, 3}));

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.sortOrder()).isEqualTo((short) 0);
        assertThat(dog.getPhotoKey()).isEqualTo("dogs/5/photos/a.jpg");
    }

    @Test
    void uploadPhoto_rejectsFourthPhoto() {
        Dog dog = ownedDog(5L, 1L);
        when(dogRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(dog));
        when(photoRepository.countByDogId(5L)).thenReturn(3L);
        assertThatThrownBy(() -> dogService.uploadPhoto(
                        5L, new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1})))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadPhoto_forbiddenForNonOwner() {
        Dog dog = ownedDog(5L, 99L);
        when(dogRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> dogService.uploadPhoto(
                        5L, new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1})))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletePhoto_reindexesRemaining() {
        Dog dog = ownedDog(5L, 1L);
        DogPhoto keep = dogPhoto(2L, 5L, "dogs/5/photos/b.jpg", (short) 1);
        DogPhoto remove = dogPhoto(1L, 5L, "dogs/5/photos/a.jpg", (short) 0);
        when(dogRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(dog));
        when(photoRepository.findByIdAndDogId(1L, 5L)).thenReturn(Optional.of(remove));
        when(photoRepository.findByDogIdOrderBySortOrderAsc(5L))
                .thenReturn(new ArrayList<>(List.of(keep)))
                .thenReturn(List.of(keep));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        dogService.deletePhoto(5L, 1L);

        ArgumentCaptor<List<DogPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getSortOrder()).isEqualTo((short) 0);
        assertThat(dog.getPhotoKey()).isEqualTo("dogs/5/photos/b.jpg");
    }

    private static DogPhoto dogPhoto(long id, long dogId, String key, short order) {
        DogPhoto p = new DogPhoto();
        setId(p, id);
        p.setDogId(dogId);
        p.setStorageKey(key);
        p.setContentType("image/jpeg");
        p.setSortOrder(order);
        return p;
    }

    private static DogRequest sampleRequest() {
        return new DogRequest(
                "Rex",
                "Berger",
                LocalDate.of(2020, 1, 1),
                "MALE",
                true,
                new BigDecimal("28.5"),
                "LARGE",
                "123",
                "HIGH",
                "GOOD",
                "GOOD",
                "GOOD",
                "LOW",
                null,
                null);
    }

    private static Dog ownedDog(long id, long ownerId) {
        Dog dog = new Dog();
        setId(dog, id);
        dog.setOwnerId(ownerId);
        dog.setName("Rex");
        dog.setBreed("Berger");
        dog.setSex("MALE");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");
        dog.setDossierStatus("DRAFT");
        return dog;
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
