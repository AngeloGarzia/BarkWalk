package com.barkwalk.user.service;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.storage.StorageService;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.ProfilePhoto;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.dto.ProfileDtos.PhotoResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileUpdateRequest;
import com.barkwalk.user.repository.ProfilePhotoRepository;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Lecture / mise à jour du profil + 1 à 3 photos.
@Service
public class ProfileService {
    private static final int MAX_PHOTOS = 3;

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ProfilePhotoRepository photoRepository;
    private final StorageService storageService;

    public ProfileService(
            ProfileRepository profileRepository,
            UserRepository userRepository,
            ProfilePhotoRepository photoRepository,
            StorageService storageService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me() {
        Long id = SecurityUtils.currentUserId();
        User user = userRepository.findById(id).orElseThrow();
        Profile profile = profileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse update(ProfileUpdateRequest request) {
        Long id = SecurityUtils.currentUserId();
        User user = userRepository.findById(id).orElseThrow();
        Profile profile = profileRepository.findById(id).orElseThrow();
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setCityId(request.cityId());
        profile.setZoneId(request.zoneId());
        profile.setUpdatedAt(Instant.now());
        return toResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listPhotos() {
        return photoRepository.findByUserIdOrderBySortOrderAsc(SecurityUtils.currentUserId()).stream()
                .map(this::toPhoto)
                .toList();
    }

    @Transactional
    public PhotoResponse uploadPhoto(MultipartFile file) {
        Long userId = SecurityUtils.currentUserId();
        if (photoRepository.countByUserId(userId) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos par profil");
        }
        short nextOrder = nextSortOrder(userId);
        String key = storageService.storeImage("profiles/" + userId, file);
        ProfilePhoto photo = new ProfilePhoto();
        photo.setUserId(userId);
        photo.setStorageKey(key);
        photo.setContentType(normalizeType(file.getContentType()));
        photo.setSortOrder(nextOrder);
        photo = photoRepository.save(photo);
        syncLegacyPhotoKey(userId);
        return toPhoto(photo);
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        Long userId = SecurityUtils.currentUserId();
        ProfilePhoto photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        storageService.deleteObject(photo.getStorageKey());
        photoRepository.delete(photo);
        reindex(userId);
        syncLegacyPhotoKey(userId);
    }

    private void reindex(Long userId) {
        List<ProfilePhoto> remaining = photoRepository.findByUserIdOrderBySortOrderAsc(userId);
        short order = 0;
        for (ProfilePhoto p : remaining) {
            p.setSortOrder(order++);
        }
        photoRepository.saveAll(remaining);
    }

    private short nextSortOrder(Long userId) {
        List<ProfilePhoto> existing = photoRepository.findByUserIdOrderBySortOrderAsc(userId);
        boolean[] used = new boolean[MAX_PHOTOS];
        for (ProfilePhoto p : existing) {
            if (p.getSortOrder() >= 0 && p.getSortOrder() < MAX_PHOTOS) {
                used[p.getSortOrder()] = true;
            }
        }
        for (short i = 0; i < MAX_PHOTOS; i++) {
            if (!used[i]) {
                return i;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos par profil");
    }

    private void syncLegacyPhotoKey(Long userId) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            return;
        }
        List<ProfilePhoto> photos = photoRepository.findByUserIdOrderBySortOrderAsc(userId);
        profile.setPhotoKey(photos.isEmpty() ? null : photos.getFirst().getStorageKey());
        profile.setUpdatedAt(Instant.now());
    }

    private ProfileResponse toResponse(User user, Profile profile) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhone(),
                profile.getBio(),
                profile.getCityId(),
                profile.getZoneId(),
                profile.getCompletedWalksCount(),
                profile.getCommunityOrgStatus(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                listPhotosFor(user.getId()));
    }

    private List<PhotoResponse> listPhotosFor(Long userId) {
        return photoRepository.findByUserIdOrderBySortOrderAsc(userId).stream().map(this::toPhoto).toList();
    }

    private PhotoResponse toPhoto(ProfilePhoto photo) {
        return new PhotoResponse(photo.getId(), storageService.publicMediaUrl(photo.getStorageKey()), photo.getSortOrder());
    }

    private static String normalizeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }
}
