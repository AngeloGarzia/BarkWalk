package com.barkwalk.walk.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.review.domain.WalkRating;
import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkRecycleRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import com.barkwalk.walk.dto.WalkDtos.WalkUpdateRequest;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantResponse;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantsResponse;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkHistoryComment;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkHistoryResponse;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkPhotoResponse;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import com.barkwalk.walker.service.WalkerPublishEligibility;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Catalogue et création de balades (encadrées ou communautaires).
@Service
public class WalkService {
    private static final int ROUTE_MIN_POINTS = 2;
    private static final int ROUTE_MAX_POINTS = 50;

    private final WalkRepository walkRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final CityRepository cityRepository;
    private final BookingRepository bookingRepository;
    private final ProfileRepository profileRepository;
    private final BusinessSettingsService businessSettingsService;
    private final PrivacyService privacyService;
    private final DogRepository dogRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final WalkerPublishEligibility walkerPublishEligibility;
    private final WalkDifficultyCalculator difficultyCalculator;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WalkRatingRepository walkRatingRepository;
    private final WalkPhotoService walkPhotoService;
    private final MessagingService messagingService;

    public WalkService(
            WalkRepository walkRepository,
            MeetingPointRepository meetingPointRepository,
            CityRepository cityRepository,
            BookingRepository bookingRepository,
            ProfileRepository profileRepository,
            BusinessSettingsService businessSettingsService,
            PrivacyService privacyService,
            DogRepository dogRepository,
            WalkerProfileRepository walkerProfileRepository,
            WalkerPublishEligibility walkerPublishEligibility,
            WalkDifficultyCalculator difficultyCalculator,
            WaitlistEntryRepository waitlistEntryRepository,
            WalkRatingRepository walkRatingRepository,
            WalkPhotoService walkPhotoService,
            MessagingService messagingService) {
        this.walkRepository = walkRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.cityRepository = cityRepository;
        this.bookingRepository = bookingRepository;
        this.profileRepository = profileRepository;
        this.businessSettingsService = businessSettingsService;
        this.privacyService = privacyService;
        this.dogRepository = dogRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.walkerPublishEligibility = walkerPublishEligibility;
        this.difficultyCalculator = difficultyCalculator;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.walkRatingRepository = walkRatingRepository;
        this.walkPhotoService = walkPhotoService;
        this.messagingService = messagingService;
    }

    @Transactional(readOnly = true)
    public List<WalkResponse> catalogue(Long cityId, String type, Instant from) {
        Instant start = from != null ? from : Instant.now();
        return walkRepository.catalogue(cityId, type, start).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WalkResponse get(Long id) {
        Walk walk = walkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertWalkVisible(walk);
        return toResponse(walk);
    }

    // Balades dont l'utilisateur est organisateur (créateur) ou encadrant assigné.
    @Transactional(readOnly = true)
    public List<WalkResponse> mine() {
        Long userId = SecurityUtils.currentUserId();
        Long walkerId = walkerProfileRepository.findByUserId(userId).map(WalkerProfile::getId).orElse(null);
        return walkRepository.findMine(userId, walkerId).stream().map(this::toResponse).toList();
    }

    // Communautaire : organisateur éligible ou actif. Encadrée : staff / indépendant / admin.
    @Transactional
    public WalkResponse create(WalkCreateRequest request) {
        var principal = SecurityUtils.currentUser();
        boolean admin = principal.getRoles().contains(Role.ADMIN);

        assertCreatePayload(request);
        assertCanPublishWalkType(request.walkType(), request.walkerId());

        if (!meetingPointRepository.existsById(request.meetingPointId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meeting point not found");
        }

        // R11 : communautaire plafonnée par configuration ; encadrée = capacité choisie par admin/indépendant.
        assertCapacityAllowed(request.walkType(), request.capacity());

        Long walkerId = resolveWalkerIdForCreate(request.walkType(), request.walkerId());

        Walk walk = new Walk();
        walk.setCityId(request.cityId());
        walk.setMeetingPointId(request.meetingPointId());
        walk.setWalkType(request.walkType());
        walk.setTitle(request.title());
        walk.setDescription(request.description());
        walk.setStartsAt(request.startsAt());
        walk.setDurationMinutes(request.durationMinutes());
        applyDifficultyInputs(walk, request.durationMinutes(), request.elevationGainM(), request.terrainType());
        walk.setCapacity(request.capacity());
        walk.setPriceCents(normalizePriceCents(request.walkType(), request.priceCents()));
        walk.setNonMemberPriceCents(request.nonMemberPriceCents());
        walk.setWalkerId(walkerId);
        // Créateur = compte qui publie (sert à la note moyenne organisateur).
        walk.setOrganizerUserId(principal.getId());
        walk.setAcceptedSizes(request.acceptedSizes().toArray(String[]::new));
        walk.setAcceptedEnergy(request.acceptedEnergy().toArray(String[]::new));
        walk.setMinSociabilityDogs(request.minSociabilityDogs());
        // Balades dédiées catégorie 1/2 : réservées à l'admin.
        if (request.allowsDangerCategories() && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can create dedicated danger walks");
        }
        walk.setAllowsDangerCategories(request.allowsDangerCategories());
        walk.setRouteHint(request.routeHint());
        walk.setRouteGeojson(normalizeRouteGeojson(request.routeGeojson()));
        walk.setStatus("PUBLISHED");
        Walk saved = walkRepository.save(walk);
        messagingService.ensureWalkThread(saved);
        return toResponse(saved);
    }

    // Modification réservée au créateur (organisateur) ou admin.
    @Transactional
    public WalkResponse update(Long walkId, WalkUpdateRequest request) {
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (walk.getArchivedAt() != null
                || "CANCELLED".equals(walk.getStatus())
                || "COMPLETED".equals(walk.getStatus())
                || "IN_PROGRESS".equals(walk.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Walk cannot be edited in status " + walk.getStatus());
        }

        requireCreatorOrAdmin(walk);

        if (!meetingPointRepository.existsById(request.meetingPointId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meeting point not found");
        }
        MeetingPoint meetingPoint = meetingPointRepository.findById(request.meetingPointId()).orElseThrow();
        if (!meetingPoint.getCityId().equals(walk.getCityId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meeting point must belong to the walk city");
        }

        assertCapacityAllowed(walk.getWalkType(), request.capacity());
        long booked = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT"));
        if (request.capacity() < booked) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Capacity cannot be below current bookings (" + booked + ")");
        }

        walk.setMeetingPointId(request.meetingPointId());
        walk.setTitle(request.title());
        walk.setDescription(request.description());
        walk.setStartsAt(request.startsAt());
        walk.setDurationMinutes(request.durationMinutes());
        applyDifficultyInputs(walk, request.durationMinutes(), request.elevationGainM(), request.terrainType());
        walk.setCapacity(request.capacity());
        walk.setAcceptedSizes(request.acceptedSizes().toArray(String[]::new));
        walk.setAcceptedEnergy(request.acceptedEnergy().toArray(String[]::new));
        walk.setMinSociabilityDogs(request.minSociabilityDogs());
        walk.setRouteHint(request.routeHint());
        walk.setRouteGeojson(normalizeRouteGeojson(request.routeGeojson()));
        walk.setUpdatedAt(Instant.now());
        return toResponse(walkRepository.save(walk));
    }

    private void applyDifficultyInputs(Walk walk, int durationMinutes, Integer elevationGainM, String terrainType) {
        int elevation = difficultyCalculator.normalizeElevation(elevationGainM);
        String terrain = difficultyCalculator.normalizeTerrain(terrainType);
        walk.setElevationGainM(elevation);
        walk.setTerrainType(terrain);
        walk.setDifficultyLevel(difficultyCalculator.compute(durationMinutes, elevation, terrain));
    }

    // Démarre la balade (organisateur / encadrant / admin) → suivi GPS côté app.
    @Transactional
    public WalkResponse start(Long walkId) {
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk, SecurityUtils.currentUserId());

        String status = walk.getStatus();
        if ("IN_PROGRESS".equals(status)) {
            return toResponse(walk);
        }
        if (!"PUBLISHED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Walk cannot be started in status " + status);
        }

        Instant now = Instant.now();
        walk.setStatus("IN_PROGRESS");
        walk.setStartedAt(now);
        walk.setCompletedAt(null);
        walk.setUpdatedAt(now);
        return toResponse(walkRepository.save(walk));
    }

    // Termine la balade en cours.
    @Transactional
    public WalkResponse complete(Long walkId) {
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk, SecurityUtils.currentUserId());

        String status = walk.getStatus();
        if ("COMPLETED".equals(status)) {
            return toResponse(walk);
        }
        if (!"IN_PROGRESS".equals(status) && !"PUBLISHED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Walk cannot be completed in status " + status);
        }

        Instant now = Instant.now();
        walk.setStatus("COMPLETED");
        if (walk.getStartedAt() == null) {
            walk.setStartedAt(now);
        }
        walk.setCompletedAt(now);
        walk.setArchivedAt(now);
        walk.setUpdatedAt(now);
        return toResponse(walkRepository.save(walk));
    }

    /**
     * Archive une balade (soft). Retire du catalogue ; pas de suppression physique.
     * Réservé au créateur / encadrant / admin.
     */
    @Transactional
    public WalkResponse archive(Long walkId) {
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk, SecurityUtils.currentUserId());

        Instant now = Instant.now();
        if (walk.getArchivedAt() != null) {
            return toResponse(walk);
        }
        String status = walk.getStatus();
        if ("PUBLISHED".equals(status) || "IN_PROGRESS".equals(status) || "DRAFT".equals(status)) {
            walk.setStatus("CANCELLED");
            walk.setCancelledByUserId(SecurityUtils.currentUserId());
            walk.setCancellationReason("archived");
            // Annule les réservations actives (pas de remboursement Stripe ici — stub côté BookingService).
            for (Booking booking : bookingRepository.findByWalkId(walk.getId())) {
                if ("CONFIRMED".equals(booking.getStatus()) || "PENDING_PAYMENT".equals(booking.getStatus())) {
                    booking.setStatus("CANCELLED_FREE");
                    booking.setCancelledAt(now);
                    booking.setCancelledByUserId(SecurityUtils.currentUserId());
                    booking.setCancelledByRole("STAFF");
                    booking.setCancellationReason("walk_archived");
                    booking.setCountsTowardOrgThreshold(false);
                    booking.setCountsTowardMembershipQuota(false);
                    bookingRepository.save(booking);
                }
            }
        }
        walk.setArchivedAt(now);
        walk.setUpdatedAt(now);
        return toResponse(walkRepository.save(walk));
    }

    /**
     * Suppression définitive : admin uniquement. Impossible s'il reste des réservations
     * (FK ON DELETE RESTRICT) — dans ce cas, archiver.
     */
    @Transactional
    public void deleteByAdmin(Long walkId) {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can delete walks");
        }
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!bookingRepository.findByWalkId(walkId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Walk has bookings and cannot be deleted; archive it instead");
        }
        waitlistEntryRepository.deleteByWalkId(walkId);
        walkRatingRepository.deleteByWalkId(walkId);
        walkRepository.delete(walk);
    }

    /** Liste admin (toutes villes). */
    @Transactional(readOnly = true)
    public List<WalkResponse> archived() {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return walkRepository.findArchived().stream().map(this::toResponse).toList();
    }

    /**
     * Balades archivées pour utilisateurs connectés.
     * Admin : toutes les archives (sans filtre ville). Autres : filtre ville / même nom de ville.
     */
    @Transactional(readOnly = true)
    public List<WalkResponse> archivedCatalogue(Long cityId) {
        SecurityUtils.currentUserId();
        boolean admin = SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        if (admin) {
            return walkRepository.findArchived().stream().map(this::toResponse).toList();
        }
        return walkRepository.findArchivedFiltered(cityId).stream().map(this::toResponse).toList();
    }

    /**
     * Vue admin catalogue : toutes les balades proposées.
     * Filtres optionnels : ville (ou même nom) et statut (ou {@code ARCHIVED} si soft-archivée).
     */
    @Transactional(readOnly = true)
    public List<WalkResponse> adminOverview(Long cityId, String status) {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String normalized = normalizeAdminStatusFilter(status);
        return walkRepository.findAdminOverview(cityId, normalized).stream().map(this::toResponse).toList();
    }

    /**
     * Agenda admin / organisateur : toutes les balades (passées et à venir), sans filtre statut.
     */
    @Transactional(readOnly = true)
    public List<WalkResponse> calendarOverview() {
        var roles = SecurityUtils.currentUser().getRoles();
        boolean allowed = roles.contains(Role.ADMIN) || roles.contains(Role.COMMUNITY_ORG);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calendar reserved to admin and organizers");
        }
        return walkRepository.findAdminOverview(null, null).stream().map(this::toResponse).toList();
    }

    private static String normalizeAdminStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "DRAFT", "PUBLISHED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ARCHIVED" -> value;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status filter");
        };
    }

    /**
     * Recycle une balade passée : copie les attributs vers une nouvelle édition à {@code startsAt}.
     * Photos et notes restent sur la lignée source (lues via {@link #history}).
     */
    @Transactional
    public WalkResponse recycle(Long sourceWalkId, WalkRecycleRequest request) {
        Walk source = walkRepository
                .findById(sourceWalkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireCreatorOrAdmin(source);

        boolean past = source.getArchivedAt() != null
                || "COMPLETED".equals(source.getStatus())
                || "CANCELLED".equals(source.getStatus());
        if (!past) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only archived/completed/cancelled walks can be recycled");
        }
        if (request.startsAt() == null || !request.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt must be in the future");
        }

        // R10 / R15 : mêmes droits que create + publication (orga révoqué / indep sans docs bloqués).
        assertCanPublishWalkType(source.getWalkType(), source.getWalkerId());
        assertCapacityAllowed(source.getWalkType(), source.getCapacity());
        if (source.isAllowsDangerCategories()
                && !SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can recycle dedicated danger walks");
        }

        Long callerId = SecurityUtils.currentUserId();
        Walk walk = new Walk();
        walk.setCityId(source.getCityId());
        walk.setMeetingPointId(source.getMeetingPointId());
        walk.setWalkType(source.getWalkType());
        walk.setTitle(source.getTitle());
        walk.setDescription(source.getDescription());
        walk.setStartsAt(request.startsAt());
        walk.setDurationMinutes(source.getDurationMinutes());
        walk.setElevationGainM(source.getElevationGainM());
        walk.setTerrainType(source.getTerrainType());
        walk.setDifficultyLevel(source.getDifficultyLevel());
        walk.setCapacity(source.getCapacity());
        walk.setPriceCents(normalizePriceCents(source.getWalkType(), source.getPriceCents()));
        walk.setNonMemberPriceCents(source.getNonMemberPriceCents());
        walk.setWalkerId(source.getWalkerId());
        walk.setOrganizerUserId(callerId);
        walk.setAcceptedSizes(
                source.getAcceptedSizes() == null ? new String[] {"SMALL", "MEDIUM"} : source.getAcceptedSizes().clone());
        walk.setAcceptedEnergy(
                source.getAcceptedEnergy() == null
                        ? new String[] {"LOW", "MEDIUM"}
                        : source.getAcceptedEnergy().clone());
        walk.setMinSociabilityDogs(source.getMinSociabilityDogs());
        walk.setAllowsDangerCategories(source.isAllowsDangerCategories());
        walk.setRouteHint(source.getRouteHint());
        walk.setRouteGeojson(source.getRouteGeojson() == null ? null : new LinkedHashMap<>(source.getRouteGeojson()));
        walk.setStatus("PUBLISHED");
        walk.setRecycledFromWalkId(source.getId());
        Walk saved = walkRepository.save(walk);
        messagingService.ensureWalkThread(saved);
        return toResponse(saved);
    }

    /** Photos + commentaires des notes ★ sur toute la lignée recycled_from. */
    @Transactional(readOnly = true)
    public WalkHistoryResponse history(Long walkId) {
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Long> lineage = lineageWalkIds(walk);
        if (lineage.isEmpty()) {
            return new WalkHistoryResponse(walkId, walk.getRecycledFromWalkId(), List.of(), List.of(), List.of());
        }

        Map<Long, Walk> byId = new LinkedHashMap<>();
        for (Long id : lineage) {
            walkRepository.findById(id).ifPresent(w -> byId.put(id, w));
        }

        List<WalkPhotoResponse> photos = walkPhotoService.listForWalks(lineage);
        List<WalkHistoryComment> comments = new ArrayList<>();
        for (WalkRating rating : walkRatingRepository.findByWalkIdInOrderByCreatedAtDesc(lineage)) {
            if (rating.getComment() == null || rating.getComment().isBlank()) {
                continue;
            }
            Walk src = byId.get(rating.getWalkId());
            // Pas d'userId en public : pseudo seulement (évite la fuite d'identifiants).
            Long exposedUserId = canSeeInternalWalkData() ? rating.getUserId() : null;
            comments.add(new WalkHistoryComment(
                    rating.getWalkId(),
                    src != null ? src.getTitle() : null,
                    src != null ? src.getStartsAt() : null,
                    exposedUserId,
                    privacyService.displayName(rating.getUserId()),
                    rating.getRating(),
                    rating.getComment(),
                    rating.getCreatedAt()));
        }
        return new WalkHistoryResponse(walkId, walk.getRecycledFromWalkId(), lineage, photos, comments);
    }

    private List<Long> lineageWalkIds(Walk walk) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Long cursor = walk.getRecycledFromWalkId();
        int guard = 0;
        while (cursor != null && guard++ < 20) {
            if (!ids.add(cursor)) {
                break;
            }
            cursor = walkRepository.findById(cursor).map(Walk::getRecycledFromWalkId).orElse(null);
        }
        return List.copyOf(ids);
    }

    private void requireCreatorOrAdmin(Walk walk) {
        var principal = SecurityUtils.currentUser();
        boolean admin = principal.getRoles().contains(Role.ADMIN);
        boolean organizer = walk.getOrganizerUserId() != null
                && walk.getOrganizerUserId().equals(principal.getId());
        if (!admin && !organizer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the walk creator can edit this walk");
        }
    }

    // Liste des inscrits : réservée à l'encadrant / organisateur / admin (téléphone conditionnel).
    @Transactional(readOnly = true)
    public ParticipantsResponse participants(Long walkId) {
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long viewerId = SecurityUtils.currentUserId();
        requireStaffForWalk(walk, viewerId);

        List<ParticipantResponse> rows = new ArrayList<>();
        for (Booking booking : bookingRepository.findByWalkId(walkId)) {
            if (!List.of("CONFIRMED", "PENDING_PAYMENT", "COMPLETED").contains(booking.getStatus())) {
                continue;
            }
            String dogName = dogRepository.findById(booking.getDogId()).map(d -> d.getName()).orElse("?");
            String phone = privacyService.phoneForViewer(viewerId, booking.getUserId(), walkId);
            rows.add(new ParticipantResponse(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getDogId(),
                    privacyService.displayName(booking.getUserId()),
                    dogName,
                    booking.getStatus(),
                    phone));
        }
        return new ParticipantsResponse(walkId, rows);
    }

    /** Pseudos visibles sur les tuiles : admin / organisateur / encadrant assigné uniquement. */
    private List<String> participantDisplayNamesIfAllowed(Walk walk) {
        Long viewerId = SecurityUtils.currentUserIdOrNull();
        if (viewerId == null || !canListParticipants(walk, viewerId)) {
            return null;
        }
        if (walk.getId() == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Booking booking : bookingRepository.findByWalkId(walk.getId())) {
            if (!List.of("CONFIRMED", "PENDING_PAYMENT", "COMPLETED").contains(booking.getStatus())) {
                continue;
            }
            String name = privacyService.displayName(booking.getUserId());
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private boolean canListParticipants(Walk walk, Long viewerId) {
        var principal = SecurityUtils.currentUser();
        if (principal.getRoles().contains(Role.ADMIN)) {
            return true;
        }
        if (walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(viewerId)) {
            return true;
        }
        if (walk.getWalkerId() != null) {
            return walkerProfileRepository.findByUserId(viewerId)
                    .map(wp -> walk.getWalkerId().equals(wp.getId()))
                    .orElse(false);
        }
        return false;
    }

    private void assertCapacityAllowed(String walkType, int capacity) {
        if ("COMMUNITY".equals(walkType)) {
            int max = businessSettingsService.communityCapacityMax();
            if (capacity > max) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Community capacity exceeds max " + max);
            }
        }
        if (capacity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity must be >= 1");
        }
    }

    /** Visibilité publique : publiée non archivée ; brouillon / privée = staff ou créateur. */
    private void assertWalkVisible(Walk walk) {
        boolean publishedVisible = "PUBLISHED".equals(walk.getStatus())
                || "IN_PROGRESS".equals(walk.getStatus())
                || "COMPLETED".equals(walk.getStatus());
        if (publishedVisible && walk.getArchivedAt() == null) {
            return;
        }
        if (canSeeInternalWalkData() || isWalkStaffOrCreator(walk)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private boolean canSeeInternalWalkData() {
        Long id = SecurityUtils.currentUserIdOrNull();
        if (id == null) {
            return false;
        }
        return SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
    }

    private boolean isWalkStaffOrCreator(Walk walk) {
        Long id = SecurityUtils.currentUserIdOrNull();
        if (id == null) {
            return false;
        }
        return canListParticipants(walk, id);
    }

    /** R10 / R15 / rôles — partagé entre create et recycle. */
    private void assertCanPublishWalkType(String walkType, Long walkerId) {
        var principal = SecurityUtils.currentUser();
        Set<Role> roles = principal.getRoles();
        boolean admin = roles.contains(Role.ADMIN);
        boolean staff = roles.contains(Role.WALKER_STAFF) || roles.contains(Role.WALKER_INDEP);

        if ("COMMUNITY".equals(walkType)) {
            Profile profile = profileRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
            int threshold = businessSettingsService.communityWalksThreshold();
            boolean orgRole = roles.contains(Role.COMMUNITY_ORG);
            boolean orgOk = orgRole
                    || "ACTIVE".equals(profile.getCommunityOrgStatus())
                    || ("ELIGIBLE".equals(profile.getCommunityOrgStatus())
                            && profile.getCompletedWalksCount() >= threshold);
            if (!admin && (!orgOk || profile.isCommunityOrgRevoked())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Community organizer rights required");
            }
            return;
        }

        if (!admin && !staff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // R15 : indépendant — docs + charte avant publication.
        if ("INDEP_GUIDED".equals(walkType) || roles.contains(Role.WALKER_INDEP)) {
            WalkerProfile walker = walkerProfileRepository.findByUserId(principal.getId()).orElse(null);
            if (walkerId != null) {
                walker = walkerProfileRepository.findById(walkerId).orElse(walker);
            }
            walkerPublishEligibility.assertCanPublish(walker);
        }
    }

    private void assertCreatePayload(WalkCreateRequest request) {
        if (request.startsAt() == null || !request.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt must be in the future");
        }
        if (request.durationMinutes() < 15 || request.durationMinutes() > 12 * 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes out of range");
        }
        if (request.priceCents() < 0
                || (request.nonMemberPriceCents() != null && request.nonMemberPriceCents() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price cannot be negative");
        }
        if ("COMMUNITY".equals(request.walkType()) && request.priceCents() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Community walks must be free");
        }
    }

    private int normalizePriceCents(String walkType, int priceCents) {
        if ("COMMUNITY".equals(walkType)) {
            return 0;
        }
        return Math.max(0, priceCents);
    }

    private Long resolveWalkerIdForCreate(String walkType, Long requestedWalkerId) {
        var principal = SecurityUtils.currentUser();
        boolean admin = principal.getRoles().contains(Role.ADMIN);
        if ("COMMUNITY".equals(walkType)) {
            return null;
        }
        if (requestedWalkerId != null) {
            WalkerProfile target = walkerProfileRepository
                    .findById(requestedWalkerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Walker not found"));
            if (!admin) {
                WalkerProfile mine = walkerProfileRepository
                        .findByUserId(principal.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Walker profile required"));
                if (!mine.getId().equals(target.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign another walker");
                }
            }
            return target.getId();
        }
        return walkerProfileRepository.findByUserId(principal.getId()).map(WalkerProfile::getId).orElse(null);
    }

    // Valide un GeoJSON LineString optionnel (2–50 points [lng, lat]).
    Map<String, Object> normalizeRouteGeojson(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        if (!"LineString".equals(String.valueOf(root.get("type")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routeGeojson must be a LineString");
        }
        Object coordsRaw = root.get("coordinates");
        if (!(coordsRaw instanceof List<?> coordsNode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routeGeojson coordinates required");
        }
        int size = coordsNode.size();
        if (size < ROUTE_MIN_POINTS || size > ROUTE_MAX_POINTS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "routeGeojson must have between " + ROUTE_MIN_POINTS + " and " + ROUTE_MAX_POINTS + " points");
        }
        List<List<Double>> cleaned = new ArrayList<>(size);
        for (Object pointObj : coordsNode) {
            if (!(pointObj instanceof List<?> point) || point.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid routeGeojson coordinate");
            }
            double lng = toDouble(point.get(0));
            double lat = toDouble(point.get(1));
            if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routeGeojson coordinate out of bounds");
            }
            cleaned.add(List.of(lng, lat));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "LineString");
        out.put("coordinates", cleaned);
        return out;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid routeGeojson coordinate");
    }

    private void requireStaffForWalk(Walk walk, Long viewerId) {
        if (!canListParticipants(walk, viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only walk staff can list participants");
        }
    }

    private WalkResponse toResponse(Walk walk) {
        long booked = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT"));
        MeetingPoint meetingPoint = meetingPointRepository.findById(walk.getMeetingPointId()).orElse(null);
        City city = cityRepository.findById(walk.getCityId()).orElse(null);
        return new WalkResponse(
                walk.getId(),
                walk.getCityId(),
                city != null ? city.getName() : null,
                city != null ? city.getDepartment() : null,
                city != null ? city.getRegion() : null,
                walk.getMeetingPointId(),
                meetingPoint != null ? meetingPoint.getName() : null,
                meetingPoint != null ? meetingPoint.getAddress() : null,
                meetingPoint != null ? meetingPoint.getLat() : null,
                meetingPoint != null ? meetingPoint.getLng() : null,
                walk.getWalkType(),
                walk.getTitle(),
                walk.getDescription(),
                walk.getStartsAt(),
                walk.getDurationMinutes(),
                walk.getElevationGainM(),
                walk.getTerrainType(),
                walk.getDifficultyLevel(),
                walk.getCapacity(),
                (int) booked,
                walk.getPriceCents(),
                walk.getNonMemberPriceCents(),
                walk.getWalkerId(),
                walk.getOrganizerUserId(),
                organizerDisplayName(walk),
                walk.getStatus(),
                walk.getStartedAt(),
                walk.getCompletedAt(),
                walk.getArchivedAt(),
                walk.getAttendanceValidatedAt(),
                walk.getRecycledFromWalkId(),
                walk.getId() == null ? List.of() : walkPhotoService.promoUrls(walk.getId(), 3),
                walk.getId() == null ? 0 : (int) walkPhotoService.galleryCount(walk.getId()),
                walk.getAcceptedSizes() == null ? List.of() : List.of(walk.getAcceptedSizes()),
                walk.getAcceptedEnergy() == null ? List.of() : List.of(walk.getAcceptedEnergy()),
                walk.getMinSociabilityDogs(),
                walk.isAllowsDangerCategories(),
                walk.getRouteHint(),
                walk.getRouteGeojson(),
                walk.isCommunityDisclaimerRequired(),
                participantDisplayNamesIfAllowed(walk));
    }

    private String organizerDisplayName(Walk walk) {
        if (walk.getOrganizerUserId() != null) {
            return privacyService.displayName(walk.getOrganizerUserId());
        }
        if (walk.getWalkerId() != null) {
            return walkerProfileRepository
                    .findById(walk.getWalkerId())
                    .map(WalkerProfile::getUserId)
                    .map(privacyService::displayName)
                    .orElse(null);
        }
        return null;
    }
}
