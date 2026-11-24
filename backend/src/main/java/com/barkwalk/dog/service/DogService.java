package com.barkwalk.dog.service;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.dog.web.DogDtos.DogRequest;
import com.barkwalk.dog.web.DogDtos.DogResponse;
import com.barkwalk.dog.web.DogDtos.DocumentRequest;
import com.barkwalk.dog.web.DogDtos.DocumentResponse;
import com.barkwalk.storage.StorageService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DogService {
    private final DogRepository dogRepository;
    private final DogDocumentRepository documentRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public DogService(DogRepository dogRepository, DogDocumentRepository documentRepository,
                      StorageService storageService, AuditService auditService) {
        this.dogRepository = dogRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DogResponse> myDogs() {
        return dogRepository.findByOwnerIdOrderByNameAsc(SecurityUtils.currentUserId()).stream().map(this::toResponse).toList();
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
        apply(dog, request);
        dog.setUpdatedAt(Instant.now());
        if (!"VALIDATED".equals(dog.getDossierStatus())) {
            dog.setDossierStatus("DRAFT");
        }
        return toResponse(dog);
    }

    @Transactional
    public void delete(Long id) {
        Dog dog = owned(id);
        dogRepository.delete(dog);
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

    @Transactional
    public DocumentResponse addDocument(Long dogId, DocumentRequest request) {
        owned(dogId);
        DogDocument doc = new DogDocument();
        doc.setDogId(dogId);
        doc.setDocType(request.docType());
        doc.setStorageKey(request.storageKey());
        doc.setExpiresAt(request.expiresAt());
        doc.setStatus("PENDING");
        doc = documentRepository.save(doc);
        return new DocumentResponse(doc.getId(), doc.getDocType(), doc.getStorageKey(),
                storageService.signedGetUrl(doc.getStorageKey()), doc.getExpiresAt(), doc.getStatus());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(Long dogId) {
        Dog dog = dogRepository.findById(dogId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var principal = SecurityUtils.currentUser();
        if (!dog.getOwnerId().equals(principal.getId()) && !principal.getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return documentRepository.findByDogId(dogId).stream()
                .map(d -> new DocumentResponse(d.getId(), d.getDocType(), d.getStorageKey(),
                        storageService.signedGetUrl(d.getStorageKey()), d.getExpiresAt(), d.getStatus()))
                .toList();
    }

    private Dog owned(Long id) {
        Dog dog = dogRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!dog.getOwnerId().equals(SecurityUtils.currentUserId()) && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return dog;
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

    private DogResponse toResponse(Dog dog) {
        return new DogResponse(dog.getId(), dog.getName(), dog.getBreed(), dog.getBirthDate(), dog.getSex(),
                dog.isSterilized(), dog.getWeightKg(), dog.getSizeCategory(), dog.getIdentificationNumber(),
                dog.getEnergyLevel(), dog.getSociabilityDogs(), dog.getSociabilityHumans(), dog.getRecallLevel(),
                dog.getLeashPulling(), dog.getAttentionNotes(), dog.getDangerCategory(), dog.getDossierStatus(),
                dog.getRefusalReason(), dog.getCreatedAt());
    }
}
