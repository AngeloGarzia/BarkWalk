package com.barkwalk.walk.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkResponse;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantResponse;
import com.barkwalk.walk.dto.WalkParticipantDtos.ParticipantsResponse;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Catalogue et création de balades (encadrées ou communautaires).
@Service
public class WalkService {
    private final WalkRepository walkRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final CityRepository cityRepository;
    private final BookingRepository bookingRepository;
    private final ProfileRepository profileRepository;
    private final BusinessSettingsService businessSettingsService;
    private final PrivacyService privacyService;
    private final DogRepository dogRepository;
    private final WalkerProfileRepository walkerProfileRepository;

    public WalkService(
            WalkRepository walkRepository,
            MeetingPointRepository meetingPointRepository,
            CityRepository cityRepository,
            BookingRepository bookingRepository,
            ProfileRepository profileRepository,
            BusinessSettingsService businessSettingsService,
            PrivacyService privacyService,
            DogRepository dogRepository,
            WalkerProfileRepository walkerProfileRepository) {
        this.walkRepository = walkRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.cityRepository = cityRepository;
        this.bookingRepository = bookingRepository;
        this.profileRepository = profileRepository;
        this.businessSettingsService = businessSettingsService;
        this.privacyService = privacyService;
        this.dogRepository = dogRepository;
        this.walkerProfileRepository = walkerProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<WalkResponse> catalogue(Long cityId, String type, Instant from) {
        Instant start = from != null ? from : Instant.now();
        return walkRepository.catalogue(cityId, type, start).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WalkResponse get(Long id) {
        return toResponse(walkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    // Communautaire : organisateur éligible ou actif. Encadrée : staff / indépendant / admin.
    @Transactional
    public WalkResponse create(WalkCreateRequest request) {
        var principal = SecurityUtils.currentUser();
        Set<Role> roles = principal.getRoles();
        boolean admin = roles.contains(Role.ADMIN);
        boolean staff = roles.contains(Role.WALKER_STAFF) || roles.contains(Role.WALKER_INDEP);

        if ("COMMUNITY".equals(request.walkType())) {
            Profile profile = profileRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
            int threshold = businessSettingsService.communityWalksThreshold();
            boolean orgOk = "ACTIVE".equals(profile.getCommunityOrgStatus())
                    || ("ELIGIBLE".equals(profile.getCommunityOrgStatus())
                            && profile.getCompletedWalksCount() >= threshold);
            if (!admin && (!orgOk || profile.isCommunityOrgRevoked())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Community organizer rights required");
            }
        } else if (!admin && !staff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (!meetingPointRepository.existsById(request.meetingPointId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meeting point not found");
        }

        // R11 : communautaire plafonnée par configuration ; encadrée = capacité choisie par admin/indépendant.
        assertCapacityAllowed(request.walkType(), request.capacity());

        Walk walk = new Walk();
        walk.setCityId(request.cityId());
        walk.setMeetingPointId(request.meetingPointId());
        walk.setWalkType(request.walkType());
        walk.setTitle(request.title());
        walk.setDescription(request.description());
        walk.setStartsAt(request.startsAt());
        walk.setDurationMinutes(request.durationMinutes());
        walk.setCapacity(request.capacity());
        walk.setPriceCents(request.priceCents());
        walk.setNonMemberPriceCents(request.nonMemberPriceCents());
        walk.setWalkerId(request.walkerId());
        if ("COMMUNITY".equals(request.walkType())) {
            walk.setOrganizerUserId(principal.getId());
        }
        walk.setAcceptedSizes(request.acceptedSizes().toArray(String[]::new));
        walk.setAcceptedEnergy(request.acceptedEnergy().toArray(String[]::new));
        walk.setMinSociabilityDogs(request.minSociabilityDogs());
        walk.setRouteHint(request.routeHint());
        walk.setStatus("PUBLISHED");
        return toResponse(walkRepository.save(walk));
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

    private void requireStaffForWalk(Walk walk, Long viewerId) {
        var principal = SecurityUtils.currentUser();
        if (principal.getRoles().contains(Role.ADMIN)) {
            return;
        }
        if (walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(viewerId)) {
            return;
        }
        if (walk.getWalkerId() != null) {
            boolean isAssignedWalker = walkerProfileRepository.findByUserId(viewerId)
                    .map(wp -> walk.getWalkerId().equals(wp.getId()))
                    .orElse(false);
            if (isAssignedWalker) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only walk staff can list participants");
    }

    private WalkResponse toResponse(Walk walk) {
        long booked = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT"));
        MeetingPoint meetingPoint = meetingPointRepository.findById(walk.getMeetingPointId()).orElse(null);
        String cityName = cityRepository.findById(walk.getCityId()).map(City::getName).orElse(null);
        return new WalkResponse(
                walk.getId(),
                walk.getCityId(),
                cityName,
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
                walk.getCapacity(),
                (int) booked,
                walk.getPriceCents(),
                walk.getNonMemberPriceCents(),
                walk.getWalkerId(),
                walk.getOrganizerUserId(),
                walk.getStatus(),
                walk.getAcceptedSizes() == null ? List.of() : List.of(walk.getAcceptedSizes()),
                walk.getAcceptedEnergy() == null ? List.of() : List.of(walk.getAcceptedEnergy()),
                walk.getMinSociabilityDogs(),
                walk.getRouteHint(),
                walk.isCommunityDisclaimerRequired());
    }
}
