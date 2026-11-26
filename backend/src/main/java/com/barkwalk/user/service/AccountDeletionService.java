package com.barkwalk.user.service;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogPhoto;
import com.barkwalk.dog.repository.DogPhotoRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.storage.StorageService;
import com.barkwalk.user.domain.ProfilePhoto;
import com.barkwalk.user.repository.ProfilePhotoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Suppression / anonymisation RGPD du compte connecté (avis & messages → « Membre supprimé » à l'affichage).
@Service
public class AccountDeletionService {
    @PersistenceContext
    private EntityManager entityManager;

    private final ProfilePhotoRepository profilePhotoRepository;
    private final DogRepository dogRepository;
    private final DogPhotoRepository dogPhotoRepository;
    private final StorageService storageService;

    public AccountDeletionService(
            ProfilePhotoRepository profilePhotoRepository,
            DogRepository dogRepository,
            DogPhotoRepository dogPhotoRepository,
            StorageService storageService) {
        this.profilePhotoRepository = profilePhotoRepository;
        this.dogRepository = dogRepository;
        this.dogPhotoRepository = dogPhotoRepository;
        this.storageService = storageService;
    }

    @Transactional
    public void deleteMyAccount() {
        Long userId = SecurityUtils.currentUserId();
        List<String> keysToDelete = new ArrayList<>();

        List<ProfilePhoto> profilePhotos = profilePhotoRepository.findByUserIdOrderBySortOrderAsc(userId);
        for (ProfilePhoto photo : profilePhotos) {
            keysToDelete.add(photo.getStorageKey());
        }
        profilePhotoRepository.deleteAll(profilePhotos);

        for (Dog dog : dogRepository.findByOwnerIdOrderByNameAsc(userId)) {
            List<DogPhoto> dogPhotos = dogPhotoRepository.findByDogIdOrderBySortOrderAsc(dog.getId());
            for (DogPhoto photo : dogPhotos) {
                keysToDelete.add(photo.getStorageKey());
            }
            dogPhotoRepository.deleteAll(dogPhotos);
        }

        entityManager.createNativeQuery("SELECT barkwalk_anonymize_user(:id)")
                .setParameter("id", userId)
                .getSingleResult();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : keysToDelete) {
                        storageService.deleteObject(key);
                    }
                }
            });
        } else {
            for (String key : keysToDelete) {
                storageService.deleteObject(key);
            }
        }
    }
}
