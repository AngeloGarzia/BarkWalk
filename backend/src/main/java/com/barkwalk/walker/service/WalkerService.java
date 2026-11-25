package com.barkwalk.walker.service;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.storage.StorageService;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.dto.WalkerDtos.ApplyRequest;
import com.barkwalk.walker.dto.WalkerDtos.TodayWalkResponse;
import com.barkwalk.walker.dto.WalkerDtos.WalkerProfileResponse;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalkerService {
    private final WalkerProfileRepository walkerProfileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final WalkRepository walkRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final BookingRepository bookingRepository;
    private final StorageService storageService;

    public WalkerService(
            WalkerProfileRepository walkerProfileRepository,
            UserRepository userRepository,
            AuditService auditService,
            WalkRepository walkRepository,
            MeetingPointRepository meetingPointRepository,
            BookingRepository bookingRepository,
            StorageService storageService) {
        this.walkerProfileRepository = walkerProfileRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.walkRepository = walkRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.bookingRepository = bookingRepository;
        this.storageService = storageService;
    }

    @Transactional
    public WalkerProfileResponse apply(ApplyRequest request) {
        Long userId = SecurityUtils.currentUserId();
        if (walkerProfileRepository.findByUserId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already applied");
        }
        WalkerProfile profile = new WalkerProfile();
        profile.setUserId(userId);
        profile.setWalkerType(request.walkerType());
        profile.setSiret(request.siret());
        if (request.diplomaFilename() != null && !request.diplomaFilename().isBlank()) {
            profile.setDiplomaKey(storageService.createObjectKey("walkers/" + userId + "/diploma", request.diplomaFilename()));
        }
        if (request.rcProFilename() != null && !request.rcProFilename().isBlank()) {
            profile.setRcProKey(storageService.createObjectKey("walkers/" + userId + "/rcpro", request.rcProFilename()));
        }
        profile.setBio(request.bio());
        profile.setStatus("PENDING");
        profile.setCharterSignedAt(Instant.now());
        return toResponse(walkerProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<TodayWalkResponse> today() {
        Long userId = SecurityUtils.currentUserId();
        WalkerProfile walker = walkerProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a walker"));
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Instant from = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return walkRepository.findForWalkerBetween(walker.getId(), from, to).stream()
                .map(this::toToday)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalkerProfileResponse> pending() {
        return walkerProfileRepository.findByStatus("PENDING").stream().map(this::toResponse).toList();
    }

    @Transactional
    public WalkerProfileResponse validate(Long id) {
        WalkerProfile profile = walkerProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        profile.setStatus("VALIDATED");
        User user = userRepository.findById(profile.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("STAFF".equals(profile.getWalkerType())) {
            user.getRoles().add(Role.WALKER_STAFF);
        } else {
            user.getRoles().add(Role.WALKER_INDEP);
        }
        auditService.log(SecurityUtils.currentUserId(), "WALKER_VALIDATE", "WALKER", id, profile.getWalkerType());
        return toResponse(profile);
    }

    private TodayWalkResponse toToday(Walk walk) {
        String address = meetingPointRepository
                .findById(walk.getMeetingPointId())
                .map(MeetingPoint::getAddress)
                .orElse("");
        long dogs = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT", "COMPLETED"));
        return new TodayWalkResponse(
                walk.getId(),
                walk.getTitle(),
                walk.getStartsAt(),
                walk.getDurationMinutes(),
                address,
                walk.getStatus(),
                dogs);
    }

    private WalkerProfileResponse toResponse(WalkerProfile p) {
        return new WalkerProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getWalkerType(),
                p.getStatus(),
                p.getBio(),
                p.getAverageRating(),
                p.getRatingsCount(),
                p.getRcProExpiresAt(),
                p.getCharterSignedAt(),
                p.getDiplomaKey() != null && !p.getDiplomaKey().isBlank(),
                p.getRcProKey() != null && !p.getRcProKey().isBlank());
    }
}
