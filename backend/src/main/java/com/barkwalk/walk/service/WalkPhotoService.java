package com.barkwalk.walk.service;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.storage.StorageService;
import com.barkwalk.storage.StorageTx;
import com.barkwalk.storage.StoredImage;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.domain.WalkPhoto;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkPhotoResponse;
import com.barkwalk.walk.repository.WalkPhotoRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalkPhotoService {
    public static final String KIND_PROMO = "PROMO";
    public static final String KIND_GALLERY = "GALLERY";
    private static final int MAX_PROMO = 6;
    private static final int MAX_GALLERY = 20;

    private final WalkRepository walkRepository;
    private final WalkPhotoRepository walkPhotoRepository;
    private final BookingRepository bookingRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final StorageService storageService;
    private final PrivacyService privacyService;

    public WalkPhotoService(
            WalkRepository walkRepository,
            WalkPhotoRepository walkPhotoRepository,
            BookingRepository bookingRepository,
            WalkerProfileRepository walkerProfileRepository,
            StorageService storageService,
            PrivacyService privacyService) {
        this.walkRepository = walkRepository;
        this.walkPhotoRepository = walkPhotoRepository;
        this.bookingRepository = bookingRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.storageService = storageService;
        this.privacyService = privacyService;
    }

    @Transactional(readOnly = true)
    public List<WalkPhotoResponse> list(Long walkId, String kindFilter) {
        walkRepository.findById(walkId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<WalkPhoto> photos = kindFilter == null || kindFilter.isBlank()
                ? walkPhotoRepository.findByWalkIdOrderByKindAscSortOrderAsc(walkId)
                : walkPhotoRepository.findByWalkIdAndKindOrderBySortOrderAsc(walkId, normalizeKind(kindFilter));
        return photos.stream().map(this::toResponse).toList();
    }

    @Transactional
    public WalkPhotoResponse upload(Long walkId, String kindRaw, MultipartFile file) {
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String kind = normalizeKind(kindRaw);
        Long userId = SecurityUtils.currentUserId();
        assertCanUpload(walk, kind, userId);

        long count = walkPhotoRepository.countByWalkIdAndKind(walkId, kind);
        int max = KIND_PROMO.equals(kind) ? MAX_PROMO : MAX_GALLERY;
        if (count >= max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum " + max + " photos for " + kind);
        }

        StoredImage stored = storageService.storeImage("walks/" + walkId + "/" + kind.toLowerCase(), file);
        StorageTx.deleteOnRollback(storageService, stored.storageKey());

        WalkPhoto photo = new WalkPhoto();
        photo.setWalkId(walkId);
        photo.setUploadedBy(userId);
        photo.setStorageKey(stored.storageKey());
        photo.setContentType(stored.contentType());
        photo.setKind(kind);
        photo.setSortOrder((short) count);
        return toResponse(walkPhotoRepository.save(photo));
    }

    @Transactional
    public void delete(Long walkId, Long photoId) {
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        WalkPhoto photo = walkPhotoRepository
                .findByIdAndWalkId(photoId, walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long userId = SecurityUtils.currentUserId();
        boolean admin = SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        boolean organizer = walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(userId);
        boolean uploader = photo.getUploadedBy().equals(userId);
        if (!admin && !organizer && !uploader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this photo");
        }
        String key = photo.getStorageKey();
        walkPhotoRepository.delete(photo);
        StorageTx.deleteAfterCommit(storageService, key);
    }

    public List<WalkPhotoResponse> listForWalks(List<Long> walkIds) {
        if (walkIds == null || walkIds.isEmpty()) {
            return List.of();
        }
        return walkPhotoRepository.findByWalkIdInOrderByWalkIdAscKindAscSortOrderAsc(walkIds).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<String> promoUrls(Long walkId, int limit) {
        return walkPhotoRepository.findByWalkIdAndKindOrderBySortOrderAsc(walkId, KIND_PROMO).stream()
                .limit(Math.max(0, limit))
                .map(p -> storageService.publicMediaUrl(p.getStorageKey()))
                .toList();
    }

    public long galleryCount(Long walkId) {
        return walkPhotoRepository.countByWalkIdAndKind(walkId, KIND_GALLERY);
    }

    private void assertCanUpload(Walk walk, String kind, Long userId) {
        boolean admin = SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        boolean organizer = walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(userId);
        boolean assignedWalker = walk.getWalkerId() != null
                && walkerProfileRepository
                        .findByUserId(userId)
                        .map(wp -> walk.getWalkerId().equals(wp.getId()))
                        .orElse(false);

        if (KIND_PROMO.equals(kind)) {
            if (!admin && !organizer) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only creator can upload promo photos");
            }
            if ("CANCELLED".equals(walk.getStatus()) || "COMPLETED".equals(walk.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add promo photos in status " + walk.getStatus());
            }
            return;
        }

        // GALLERY
        String status = walk.getStatus();
        if (!"IN_PROGRESS".equals(status) && !"COMPLETED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Gallery photos only when walk is in progress or completed");
        }
        if (admin || organizer || assignedWalker) {
            return;
        }
        boolean participant = bookingRepository.findByWalkId(walk.getId()).stream()
                .anyMatch(b -> userId.equals(b.getUserId())
                        && ("CONFIRMED".equals(b.getStatus()) || "COMPLETED".equals(b.getStatus())));
        if (!participant) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only organizer or participants can upload gallery photos");
        }
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind required (PROMO|GALLERY)");
        }
        String normalized = kind.trim().toUpperCase();
        if (!KIND_PROMO.equals(normalized) && !KIND_GALLERY.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be PROMO or GALLERY");
        }
        return normalized;
    }

    private WalkPhotoResponse toResponse(WalkPhoto photo) {
        return new WalkPhotoResponse(
                photo.getId(),
                photo.getWalkId(),
                photo.getKind(),
                storageService.publicMediaUrl(photo.getStorageKey()),
                photo.getSortOrder(),
                photo.getCaption(),
                photo.getUploadedBy(),
                privacyService.displayName(photo.getUploadedBy()),
                photo.getCreatedAt());
    }
}
