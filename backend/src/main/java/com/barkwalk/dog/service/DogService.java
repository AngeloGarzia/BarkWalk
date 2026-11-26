package com.barkwalk.dog.service;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.domain.DogPhoto;
import com.barkwalk.dog.dto.DogDtos.DogRequest;
import com.barkwalk.dog.dto.DogDtos.DogResponse;
import com.barkwalk.dog.dto.DogDtos.DocumentRequest;
import com.barkwalk.dog.dto.DogDtos.DocumentResponse;
import com.barkwalk.dog.dto.DogDtos.PhotoResponse;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogPhotoRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StorageTx;
import com.barkwalk.storage.StoredImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// CRUD chiens, photos (1–3) et pièces du dossier (propriétaire uniquement).
@Service
public class DogService {
    private static final int MAX_PHOTOS = 3;

    /** Champs identité / sécurité / matching balades : modification → revalidation admin. */
    private static final String SENSITIVE_EDIT_AUDIT = "DOG_SENSITIVE_EDIT";

    private final DogRepository dogRepository;
    private final DogDocumentRepository documentRepository;
    private final DogPhotoRepository photoRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final DogDossierService dogDossierService;

    public DogService(
            DogRepository dogRepository,
            DogDocumentRepository documentRepository,
            DogPhotoRepository photoRepository,
            StorageService storageService,
            AuditService auditService,
            DogDossierService dogDossierService) {
        this.dogRepository = dogRepository;
        this.documentRepository = documentRepository;
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.dogDossierService = dogDossierService;
    }

    @Transactional(readOnly = true)
    public List<DogResponse> myDogs() {
        return dogRepository.findByOwnerIdOrderByNameAsc(SecurityUtils.currentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DogResponse create(DogRequest request) {
        Dog dog = new Dog();
        dog.setOwnerId(SecurityUtils.currentUserId());
        apply(dog, request);
        dog.setDossierStatus("DRAFT");
        return toResponse(dogRepository.save(dog));
    }

    @Transactional
    public DogResponse update(Long id, DogRequest request) {
        Dog dog = owned(id);
        boolean wasValidated = "VALIDATED".equals(dog.getDossierStatus());
        boolean sensitiveChanged = sensitiveFieldsChanged(dog, request);
        apply(dog, request);
        dog.setUpdatedAt(Instant.now());
        if (wasValidated && sensitiveChanged) {
            // Profil déjà validé : champs sensibles → repasse en file d’attente admin.
            dog.setDossierStatus("PENDING");
            dog.setRefusalReason(null);
            auditService.log(
                    SecurityUtils.currentUserId(),
                    SENSITIVE_EDIT_AUDIT,
                    "DOG",
                    id,
                    "Modification sensible — revalidation requise");
        } else if (!wasValidated) {
            dog.setDossierStatus("DRAFT");
        }
        return toResponse(dog);
    }

    @Transactional
    public void delete(Long id) {
        Dog dog = owned(id);
        List<String> keys = photoRepository.findByDogIdOrderBySortOrderAsc(id).stream()
                .map(DogPhoto::getStorageKey)
                .toList();
        photoRepository.deleteByDogId(id);
        dogRepository.delete(dog);
        for (String key : keys) {
            StorageTx.deleteAfterCommit(storageService, key);
        }
    }

    @Transactional
    public DogResponse submit(Long id) {
        Dog dog = owned(id);
        dog.setDossierStatus("PENDING");
        dog.setUpdatedAt(Instant.now());
        return toResponse(dog);
    }

    @Transactional(readOnly = true)
    public List<DogResponse> pending() {
        return dogRepository.findByDossierStatus("PENDING").stream().map(this::toResponse).toList();
    }

    @Transactional
    public DogResponse validate(Long id) {
        requireAdmin();
        Dog dog = dogRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // R2 : validation admin seulement si vaccination + assurance RC validées et non expirées.
        dogDossierService.assertReadyForAdminValidation(id);
        dog.setDossierStatus("VALIDATED");
        dog.setRefusalReason(null);
        dog.setUpdatedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "DOG_VALIDATE", "DOG", id, null);
        return toResponse(dog);
    }

    @Transactional
    public DogResponse refuse(Long id, String reason) {
        requireAdmin();
        Dog dog = dogRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        dog.setDossierStatus("REFUSED");
        dog.setRefusalReason(reason);
        dog.setUpdatedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "DOG_REFUSE", "DOG", id, reason);
        return toResponse(dog);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listPhotos(Long dogId) {
        owned(dogId);
        return photoRepository.findByDogIdOrderBySortOrderAsc(dogId).stream().map(this::toPhoto).toList();
    }

    @Transactional
    public PhotoResponse uploadPhoto(Long dogId, MultipartFile file) {
        Dog dog = lockOwned(dogId);
        if (photoRepository.countByDogId(dog.getId()) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos par chien");
        }
        short nextOrder = nextSortOrder(dogId);
        StoredImage stored = storageService.storeImage("dogs/" + dogId + "/photos", file);
        StorageTx.deleteOnRollback(storageService, stored.storageKey());
        DogPhoto photo = new DogPhoto();
        photo.setDogId(dogId);
        photo.setStorageKey(stored.storageKey());
        photo.setContentType(stored.contentType());
        photo.setSortOrder(nextOrder);
        photo = photoRepository.save(photo);
        syncLegacyPhotoKey(dogId);
        return toPhoto(photo);
    }

    @Transactional
    public void deletePhoto(Long dogId, Long photoId) {
        lockOwned(dogId);
        DogPhoto photo = photoRepository.findByIdAndDogId(photoId, dogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String key = photo.getStorageKey();
        photoRepository.delete(photo);
        reindex(dogId);
        syncLegacyPhotoKey(dogId);
        StorageTx.deleteAfterCommit(storageService, key);
    }

    @Transactional
    public DocumentResponse addDocument(Long dogId, DocumentRequest request) {
        owned(dogId);
        String storageKey = storageService.createObjectKey("dogs/" + dogId, request.filename());
        DogDocument doc = new DogDocument();
        doc.setDogId(dogId);
        doc.setDocType(request.docType());
        doc.setStorageKey(storageKey);
        doc.setExpiresAt(request.expiresAt());
        doc.setStatus("PENDING");
        doc = documentRepository.save(doc);
        return new DocumentResponse(
                doc.getId(),
                doc.getDocType(),
                storageService.signedGetUrl(storageKey),
                storageService.signedPutUrl(storageKey),
                doc.getExpiresAt(),
                doc.getStatus());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(Long dogId) {
        Dog dog = dogRepository.findById(dogId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var principal = SecurityUtils.currentUser();
        if (!dog.getOwnerId().equals(principal.getId()) && !principal.getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return documentRepository.findByDogId(dogId).stream()
                .map(d -> new DocumentResponse(
                        d.getId(),
                        d.getDocType(),
                        storageService.signedGetUrl(d.getStorageKey()),
                        null,
                        d.getExpiresAt(),
                        d.getStatus()))
                .toList();
    }

    private void reindex(Long dogId) {
        List<DogPhoto> remaining = photoRepository.findByDogIdOrderBySortOrderAsc(dogId);
        short order = 0;
        for (DogPhoto p : remaining) {
            p.setSortOrder(order++);
        }
        photoRepository.saveAll(remaining);
    }

    private short nextSortOrder(Long dogId) {
        List<DogPhoto> existing = photoRepository.findByDogIdOrderBySortOrderAsc(dogId);
        boolean[] used = new boolean[MAX_PHOTOS];
        for (DogPhoto p : existing) {
            if (p.getSortOrder() >= 0 && p.getSortOrder() < MAX_PHOTOS) {
                used[p.getSortOrder()] = true;
            }
        }
        for (short i = 0; i < MAX_PHOTOS; i++) {
            if (!used[i]) {
                return i;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos par chien");
    }

    private void syncLegacyPhotoKey(Long dogId) {
        Dog dog = dogRepository.findById(dogId).orElse(null);
        if (dog == null) {
            return;
        }
        List<DogPhoto> photos = photoRepository.findByDogIdOrderBySortOrderAsc(dogId);
        dog.setPhotoKey(photos.isEmpty() ? null : photos.getFirst().getStorageKey());
        dog.setUpdatedAt(Instant.now());
    }

    private Dog owned(Long id) {
        Dog dog = dogRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertOwnerOrAdmin(dog);
        return dog;
    }

    private Dog lockOwned(Long id) {
        Dog dog = dogRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertOwnerOrAdmin(dog);
        return dog;
    }

    private void assertOwnerOrAdmin(Dog dog) {
        if (!dog.getOwnerId().equals(SecurityUtils.currentUserId())
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin() {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void apply(Dog dog, DogRequest request) {
        dog.setName(request.name());
        dog.setBreed(request.breed());
        dog.setBirthDate(request.birthDate());
        dog.setSex(request.sex());
        dog.setSterilized(request.sterilized());
        dog.setWeightKg(request.weightKg());
        dog.setSizeCategory(request.sizeCategory());
        dog.setIdentificationNumber(request.identificationNumber());
        dog.setEnergyLevel(request.energyLevel());
        dog.setSociabilityDogs(request.sociabilityDogs());
        dog.setSociabilityHumans(request.sociabilityHumans());
        dog.setRecallLevel(request.recallLevel());
        dog.setLeashPulling(request.leashPulling());
        dog.setAttentionNotes(request.attentionNotes());
        dog.setDangerCategory(request.dangerCategory());
    }

    /**
     * Champs libres (sans revalidation) : name, weightKg, recallLevel, leashPulling, attentionNotes.
     * Le reste du profil (identité, taille, énergie, sociabilité, danger, ID…) est sensible.
     */
    static boolean sensitiveFieldsChanged(Dog dog, DogRequest request) {
        return !Objects.equals(normalize(dog.getBreed()), normalize(request.breed()))
                || !Objects.equals(dog.getBirthDate(), request.birthDate())
                || !Objects.equals(normalize(dog.getSex()), normalize(request.sex()))
                || dog.isSterilized() != request.sterilized()
                || !Objects.equals(normalize(dog.getSizeCategory()), normalize(request.sizeCategory()))
                || !Objects.equals(
                        normalize(dog.getIdentificationNumber()), normalize(request.identificationNumber()))
                || !Objects.equals(normalize(dog.getEnergyLevel()), normalize(request.energyLevel()))
                || !Objects.equals(normalize(dog.getSociabilityDogs()), normalize(request.sociabilityDogs()))
                || !Objects.equals(
                        normalize(dog.getSociabilityHumans()), normalize(request.sociabilityHumans()))
                || !Objects.equals(normalize(dog.getDangerCategory()), normalize(request.dangerCategory()));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DogResponse toResponse(Dog dog) {
        List<PhotoResponse> photos =
                photoRepository.findByDogIdOrderBySortOrderAsc(dog.getId()).stream().map(this::toPhoto).toList();
        return new DogResponse(
                dog.getId(),
                dog.getName(),
                dog.getBreed(),
                dog.getBirthDate(),
                dog.getSex(),
                dog.isSterilized(),
                dog.getWeightKg(),
                dog.getSizeCategory(),
                dog.getIdentificationNumber(),
                dog.getEnergyLevel(),
                dog.getSociabilityDogs(),
                dog.getSociabilityHumans(),
                dog.getRecallLevel(),
                dog.getLeashPulling(),
                dog.getAttentionNotes(),
                dog.getDangerCategory(),
                dog.getDossierStatus(),
                dog.getRefusalReason(),
                dog.getCreatedAt(),
                photos);
    }

    private PhotoResponse toPhoto(DogPhoto photo) {
        return new PhotoResponse(photo.getId(), storageService.publicMediaUrl(photo.getStorageKey()), photo.getSortOrder());
    }
}
