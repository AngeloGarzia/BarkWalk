package com.barkwalk.user.service;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StorageTx;
import com.barkwalk.storage.StoredImage;
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
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Lecture / mise à jour du profil + 1 à 3 photos + revue admin identité.
@Service
public class ProfileService {
    private static final int MAX_PHOTOS = 3;
    private static final String SENSITIVE_EDIT_AUDIT = "PROFILE_SENSITIVE_EDIT";

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ProfilePhotoRepository photoRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public ProfileService(
            ProfileRepository profileRepository,
            UserRepository userRepository,
            ProfilePhotoRepository photoRepository,
            StorageService storageService,
            AuditService auditService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.storageService = storageService;
        this.auditService = auditService;
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
        boolean wasValidated = "VALIDATED".equals(profile.getReviewStatus());
        boolean wasRefused = "REFUSED".equals(profile.getReviewStatus());
        boolean sensitiveChanged = sensitiveFieldsChanged(profile, request);

        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPseudo(normalizePseudo(request.pseudo(), id));
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setCityId(request.cityId());
        profile.setZoneId(request.zoneId());
        profile.setUpdatedAt(Instant.now());

        if ((wasValidated && sensitiveChanged) || (wasRefused && sensitiveChanged)) {
            profile.setReviewStatus("PENDING");
            profile.setReviewRefusalReason(null);
            auditService.log(
                    id,
                    SENSITIVE_EDIT_AUDIT,
                    "PROFILE",
                    id,
                    "Modification sensible — revalidation admin requise");
        }

        return toResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> pendingReviews() {
        requireAdmin();
        return profileRepository.findByReviewStatusOrderByUpdatedAtAsc("PENDING").stream()
                .map(profile -> {
                    User user = userRepository.findById(profile.getUserId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    return toResponse(user, profile);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public ProfileResponse validateReview(Long userId) {
        requireAdmin();
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        profile.setReviewStatus("VALIDATED");
        profile.setReviewRefusalReason(null);
        profile.setUpdatedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "PROFILE_VALIDATE", "PROFILE", userId, null);
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse refuseReview(Long userId, String reason) {
        requireAdmin();
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        profile.setReviewStatus("REFUSED");
        profile.setReviewRefusalReason(reason);
        profile.setUpdatedAt(Instant.now());
        auditService.log(SecurityUtils.currentUserId(), "PROFILE_REFUSE", "PROFILE", userId, reason);
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
        profileRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (photoRepository.countByUserId(userId) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 3 photos par profil");
        }
        short nextOrder = nextSortOrder(userId);
        StoredImage stored = storageService.storeImage("profiles/" + userId, file);
        StorageTx.deleteOnRollback(storageService, stored.storageKey());
        ProfilePhoto photo = new ProfilePhoto();
        photo.setUserId(userId);
        photo.setStorageKey(stored.storageKey());
        photo.setContentType(stored.contentType());
        photo.setSortOrder(nextOrder);
        photo = photoRepository.save(photo);
        syncLegacyPhotoKey(userId);
        return toPhoto(photo);
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        Long userId = SecurityUtils.currentUserId();
        profileRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ProfilePhoto photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String key = photo.getStorageKey();
        photoRepository.delete(photo);
        reindex(userId);
        syncLegacyPhotoKey(userId);
        StorageTx.deleteAfterCommit(storageService, key);
    }

    /**
     * Sensibles : prénom, nom, téléphone, ville, zone.
     * Libres : bio, pseudo (et photos via endpoints dédiés).
     */
    static boolean sensitiveFieldsChanged(Profile profile, ProfileUpdateRequest request) {
        return !Objects.equals(normalize(profile.getFirstName()), normalize(request.firstName()))
                || !Objects.equals(normalize(profile.getLastName()), normalize(request.lastName()))
                || !Objects.equals(normalize(profile.getPhone()), normalize(request.phone()))
                || !Objects.equals(profile.getCityId(), request.cityId())
                || !Objects.equals(profile.getZoneId(), request.zoneId());
    }

    private String normalizePseudo(String raw, Long userId) {
        String pseudo = normalize(raw);
        if (pseudo == null) {
            return null;
        }
        if (pseudo.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pseudo must be at least 3 characters");
        }
        if (!pseudo.matches("^[\\p{L}\\p{N}_.-]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Pseudo may only contain letters, digits, _ . -");
        }
        if (profileRepository.existsPseudoIgnoreCase(pseudo, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pseudo already taken");
        }
        return pseudo;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private void requireAdmin() {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private ProfileResponse toResponse(User user, Profile profile) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPseudo(),
                profile.getPhone(),
                profile.getBio(),
                profile.getCityId(),
                profile.getZoneId(),
                profile.getCompletedWalksCount(),
                profile.getAverageRating(),
                profile.getRatingsCount(),
                profile.getCommunityOrgStatus(),
                profile.getReviewStatus(),
                profile.getReviewRefusalReason(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                listPhotosFor(user.getId()));
    }

    private List<PhotoResponse> listPhotosFor(Long userId) {
        return photoRepository.findByUserIdOrderBySortOrderAsc(userId).stream().map(this::toPhoto).toList();
    }

    private PhotoResponse toPhoto(ProfilePhoto photo) {
        return new PhotoResponse(photo.getId(), storageService.publicMediaUrl(photo.getStorageKey()), photo.getSortOrder());
    }
}
