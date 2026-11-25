package com.barkwalk.walk.service;

import com.barkwalk.admin.domain.Setting;
import com.barkwalk.admin.repository.SettingRepository;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.web.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.web.WalkDtos.WalkResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalkService {
    private final WalkRepository walkRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final CityRepository cityRepository;
    private final BookingRepository bookingRepository;
    private final ProfileRepository profileRepository;
    private final SettingRepository settingRepository;

    public WalkService(
            WalkRepository walkRepository,
            MeetingPointRepository meetingPointRepository,
            CityRepository cityRepository,
            BookingRepository bookingRepository,
            ProfileRepository profileRepository,
            SettingRepository settingRepository) {
        this.walkRepository = walkRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.cityRepository = cityRepository;
        this.bookingRepository = bookingRepository;
        this.profileRepository = profileRepository;
        this.settingRepository = settingRepository;
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

    @Transactional
    public WalkResponse create(WalkCreateRequest request) {
        var principal = SecurityUtils.currentUser();
        Set<Role> roles = principal.getRoles();
        boolean admin = roles.contains(Role.ADMIN);
        boolean staff = roles.contains(Role.WALKER_STAFF) || roles.contains(Role.WALKER_INDEP);

        if ("COMMUNITY".equals(request.walkType())) {
            Profile profile = profileRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
            int threshold = Integer.parseInt(settingRepository.findById("community_walks_threshold")
                    .map(Setting::getValue).orElse("3"));
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
                walk.getRouteHint(),
                walk.isCommunityDisclaimerRequired());
    }
}
