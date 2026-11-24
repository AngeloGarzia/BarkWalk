package com.barkwalk.walker.web;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/walkers")
public class WalkerController {
    private final WalkerProfileRepository walkerProfileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final WalkRepository walkRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final BookingRepository bookingRepository;

    public WalkerController(
            WalkerProfileRepository walkerProfileRepository,
            UserRepository userRepository,
            AuditService auditService,
            WalkRepository walkRepository,
            MeetingPointRepository meetingPointRepository,
            BookingRepository bookingRepository) {
        this.walkerProfileRepository = walkerProfileRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.walkRepository = walkRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.bookingRepository = bookingRepository;
    }

    public record TodayWalkDto(
            Long id,
            String title,
            Instant startsAt,
            int durationMinutes,
            String address,
            String status,
            long dogCount) {}

    public record ApplyRequest(@NotBlank String walkerType, String siret, String diplomaKey, String rcProKey, String bio) {}

    @PostMapping("/apply")
    @Transactional
    public WalkerProfile apply(@RequestBody ApplyRequest request) {
        Long userId = SecurityUtils.currentUserId();
        if (walkerProfileRepository.findByUserId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already applied");
        }
        WalkerProfile profile = new WalkerProfile();
        profile.setUserId(userId);
        profile.setWalkerType(request.walkerType());
        profile.setSiret(request.siret());
        profile.setDiplomaKey(request.diplomaKey());
        profile.setRcProKey(request.rcProKey());
        profile.setBio(request.bio());
        profile.setStatus("PENDING");
        profile.setCharterSignedAt(Instant.now());
        return walkerProfileRepository.save(profile);
    }

    @GetMapping("/today")
    @Transactional(readOnly = true)
    public List<TodayWalkDto> today() {
        Long userId = SecurityUtils.currentUserId();
        WalkerProfile walker = walkerProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a walker"));
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Instant from = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return walkRepository.findForWalkerBetween(walker.getId(), from, to).stream()
                .map(this::toTodayDto)
                .toList();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WalkerProfile> pending() {
        return walkerProfileRepository.findByStatus("PENDING");
    }

    private TodayWalkDto toTodayDto(Walk walk) {
        String address = meetingPointRepository
                .findById(walk.getMeetingPointId())
                .map(MeetingPoint::getAddress)
                .orElse("");
        long dogs = bookingRepository.countByWalkIdAndStatusIn(
                walk.getId(), List.of("CONFIRMED", "PENDING_PAYMENT", "COMPLETED"));
        return new TodayWalkDto(
                walk.getId(),
                walk.getTitle(),
                walk.getStartsAt(),
                walk.getDurationMinutes(),
                address,
                walk.getStatus(),
                dogs);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WalkerProfile validate(@PathVariable Long id) {
        WalkerProfile profile = walkerProfileRepository.findById(id).orElseThrow();
        profile.setStatus("VALIDATED");
        User user = userRepository.findById(profile.getUserId()).orElseThrow();
        if ("STAFF".equals(profile.getWalkerType())) {
            user.getRoles().add(Role.WALKER_STAFF);
        } else {
            user.getRoles().add(Role.WALKER_INDEP);
        }
        auditService.log(SecurityUtils.currentUserId(), "WALKER_VALIDATE", "WALKER", id, profile.getWalkerType());
        return profile;
    }
}
