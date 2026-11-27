package com.barkwalk.attendance.service;

import com.barkwalk.attendance.domain.Attendance;
import com.barkwalk.attendance.domain.WalkReport;
import com.barkwalk.attendance.domain.WalkReportDogNote;
import com.barkwalk.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.DogNote;
import com.barkwalk.attendance.dto.AttendanceDtos.PendingRollcallWalk;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.ReportRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallItem;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.RosterEntry;
import com.barkwalk.attendance.dto.AttendanceDtos.RosterResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.WalkReportResponse;
import com.barkwalk.attendance.repository.AttendanceRepository;
import com.barkwalk.attendance.repository.WalkReportDogNoteRepository;
import com.barkwalk.attendance.repository.WalkReportRepository;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.user.service.CommunityOrgService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final BookingRepository bookingRepository;
    private final WalkReportRepository walkReportRepository;
    private final WalkReportDogNoteRepository noteRepository;
    private final CommunityOrgService communityOrgService;
    private final WalkRepository walkRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final CityRepository cityRepository;
    private final DogRepository dogRepository;
    private final PrivacyService privacyService;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            BookingRepository bookingRepository,
            WalkReportRepository walkReportRepository,
            WalkReportDogNoteRepository noteRepository,
            CommunityOrgService communityOrgService,
            WalkRepository walkRepository,
            WalkerProfileRepository walkerProfileRepository,
            CityRepository cityRepository,
            DogRepository dogRepository,
            PrivacyService privacyService) {
        this.attendanceRepository = attendanceRepository;
        this.bookingRepository = bookingRepository;
        this.walkReportRepository = walkReportRepository;
        this.noteRepository = noteRepository;
        this.communityOrgService = communityOrgService;
        this.walkRepository = walkRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.cityRepository = cityRepository;
        this.dogRepository = dogRepository;
        this.privacyService = privacyService;
    }

    @Transactional
    public AttendanceResponse departure(PointRequest request) {
        Booking booking = bookingRepository
                .findById(request.bookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Walk walk = walkRepository
                .findById(booking.getWalkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk);
        return toAttendance(point(booking, request.status(), true));
    }

    @Transactional
    public AttendanceResponse markReturn(PointRequest request) {
        Booking booking = bookingRepository
                .findById(request.bookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Walk walk = walkRepository
                .findById(booking.getWalkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk);
        Attendance attendance = point(booking, request.status(), false);
        if ("PRESENT".equalsIgnoreCase(request.status())) {
            communityOrgService.onWalkCompleted(booking.getUserId());
            booking.setStatus("COMPLETED");
            bookingRepository.save(booking);
        }
        return toAttendance(attendance);
    }

    @Transactional
    public WalkReportResponse report(ReportRequest request) {
        Walk walk = walkRepository
                .findById(request.walkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk);
        WalkReport report = walkReportRepository.findByWalkId(request.walkId()).orElseGet(WalkReport::new);
        report.setWalkId(request.walkId());
        report.setAuthorId(SecurityUtils.currentUserId());
        report.setActualDurationMinutes(request.actualDurationMinutes());
        report.setRouteDone(request.routeDone());
        report = walkReportRepository.save(report);
        if (request.dogNotes() != null) {
            for (DogNote note : request.dogNotes()) {
                WalkReportDogNote n = new WalkReportDogNote();
                n.setReportId(report.getId());
                n.setDogId(note.dogId());
                n.setBehaviorScore(note.behaviorScore());
                n.setComment(note.comment());
                noteRepository.save(n);
            }
        }
        return toReport(report);
    }

    /**
     * Balades dont l'heure de fin est dépassée et le pointage n'est pas validé —
     * pour l'organisateur / encadrant / admin (rappel menu Balades).
     */
    @Transactional(readOnly = true)
    public List<PendingRollcallWalk> pendingRollcalls() {
        Long userId = SecurityUtils.currentUserId();
        boolean admin = SecurityUtils.currentUser().getRoles().contains(Role.ADMIN);
        Long walkerId = walkerProfileRepository.findByUserId(userId).map(wp -> wp.getId()).orElse(null);
        Instant now = Instant.now();
        List<Walk> candidates =
                admin
                        ? walkRepository.findPendingAttendanceCandidates()
                        : walkRepository.findMinePendingAttendance(userId, walkerId);
        List<PendingRollcallWalk> result = new ArrayList<>();
        for (Walk walk : candidates) {
            Instant endsAt = walkEndsAt(walk);
            if (endsAt == null || !endsAt.isBefore(now)) {
                continue;
            }
            long confirmed = bookingRepository.countByWalkIdAndStatusIn(walk.getId(), List.of("CONFIRMED"));
            if (confirmed == 0) {
                continue;
            }
            String cityName = cityRepository.findById(walk.getCityId()).map(c -> c.getName()).orElse(null);
            result.add(new PendingRollcallWalk(
                    walk.getId(),
                    walk.getTitle(),
                    cityName,
                    walk.getStartsAt(),
                    walk.getDurationMinutes(),
                    endsAt,
                    walk.getStatus(),
                    (int) confirmed));
        }
        result.sort((a, b) -> a.endsAt().compareTo(b.endsAt()));
        return result;
    }

    @Transactional(readOnly = true)
    public RosterResponse roster(Long walkId) {
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk);
        List<RosterEntry> entries = new ArrayList<>();
        for (Booking booking : bookingRepository.findByWalkId(walkId)) {
            if (!"CONFIRMED".equals(booking.getStatus())
                    && !"COMPLETED".equals(booking.getStatus())
                    && !"NO_SHOW".equals(booking.getStatus())) {
                continue;
            }
            String dogName = dogRepository.findById(booking.getDogId()).map(d -> d.getName()).orElse("?");
            String attendanceStatus = attendanceRepository
                    .findByBookingId(booking.getId())
                    .map(Attendance::getDepartureStatus)
                    .orElse(null);
            entries.add(new RosterEntry(
                    booking.getId(),
                    booking.getUserId(),
                    privacyService.displayName(booking.getUserId()),
                    booking.getDogId(),
                    dogName,
                    booking.getStatus(),
                    attendanceStatus));
        }
        return new RosterResponse(
                walk.getId(),
                walk.getTitle(),
                walk.getStartsAt(),
                walkEndsAt(walk),
                walk.getAttendanceValidatedAt(),
                entries);
    }

    /**
     * Valide le pointage : chaque inscrit CONFIRMED doit être PRESENT ou ABSENT.
     * Met à jour bookings (COMPLETED / NO_SHOW), attendance, et marque la balade validée.
     */
    @Transactional
    public RollcallResponse submitRollcall(Long walkId, RollcallRequest request) {
        Walk walk = walkRepository
                .findById(walkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireStaffForWalk(walk);
        Instant now = Instant.now();
        Instant endsAt = walkEndsAt(walk);
        if (endsAt == null || !endsAt.isBefore(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rollcall available only after walk end time");
        }
        if (walk.getAttendanceValidatedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance already validated");
        }

        List<Booking> confirmed = bookingRepository.findByWalkId(walkId).stream()
                .filter(b -> "CONFIRMED".equals(b.getStatus()))
                .toList();
        if (confirmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No confirmed bookings to validate");
        }

        Map<Long, String> byBooking = new HashMap<>();
        for (RollcallItem item : request.items()) {
            String status = normalizePresence(item.status());
            byBooking.put(item.bookingId(), status);
        }
        Set<Long> expected = new HashSet<>();
        for (Booking booking : confirmed) {
            expected.add(booking.getId());
            if (!byBooking.containsKey(booking.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Missing attendance for booking " + booking.getId());
            }
        }
        for (Long id : byBooking.keySet()) {
            if (!expected.contains(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown booking " + id);
            }
        }

        int present = 0;
        int absent = 0;
        for (Booking booking : confirmed) {
            String status = byBooking.get(booking.getId());
            point(booking, status, true);
            if ("PRESENT".equals(status)) {
                booking.setStatus("COMPLETED");
                communityOrgService.onWalkCompleted(booking.getUserId());
                present++;
            } else {
                booking.setStatus("NO_SHOW");
                absent++;
            }
            bookingRepository.save(booking);
        }

        walk.setAttendanceValidatedAt(now);
        if (!"COMPLETED".equals(walk.getStatus()) && !"CANCELLED".equals(walk.getStatus())) {
            walk.setStatus("COMPLETED");
            if (walk.getCompletedAt() == null) {
                walk.setCompletedAt(now);
            }
        }
        walk.setUpdatedAt(now);
        walkRepository.save(walk);

        return new RollcallResponse(walk.getId(), walk.getAttendanceValidatedAt(), present, absent);
    }

    private Attendance point(Booking booking, String status, boolean departure) {
        String normalized = normalizePresence(status);
        Attendance attendance = attendanceRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            Attendance a = new Attendance();
            a.setBookingId(booking.getId());
            return a;
        });
        if (departure) {
            attendance.setDepartureStatus(normalized);
            attendance.setDepartureAt(Instant.now());
        } else {
            attendance.setReturnStatus(normalized);
            attendance.setReturnAt(Instant.now());
        }
        return attendanceRepository.save(attendance);
    }

    private static String normalizePresence(String status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "PRESENT", "ABSENT", "LATE" -> value;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "status must be PRESENT, ABSENT or LATE");
        };
    }

    private Instant walkEndsAt(Walk walk) {
        if (walk.getStartsAt() == null) {
            return null;
        }
        int minutes = walk.getDurationMinutes() > 0 ? walk.getDurationMinutes() : 60;
        return walk.getStartsAt().plus(Duration.ofMinutes(minutes));
    }

    private void requireStaffForWalk(Walk walk) {
        Long viewerId = SecurityUtils.currentUserId();
        var roles = SecurityUtils.currentUser().getRoles();
        if (roles.contains(Role.ADMIN)) {
            return;
        }
        if (walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(viewerId)) {
            return;
        }
        if (walk.getWalkerId() != null) {
            boolean assigned = walkerProfileRepository
                    .findByUserId(viewerId)
                    .map(wp -> walk.getWalkerId().equals(wp.getId()))
                    .orElse(false);
            if (assigned) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only walk staff can manage attendance");
    }

    private AttendanceResponse toAttendance(Attendance a) {
        return new AttendanceResponse(
                a.getId(),
                a.getBookingId(),
                a.getDepartureStatus(),
                a.getDepartureAt(),
                a.getReturnStatus(),
                a.getReturnAt());
    }

    private WalkReportResponse toReport(WalkReport r) {
        return new WalkReportResponse(
                r.getId(),
                r.getWalkId(),
                r.getAuthorId(),
                r.getActualDurationMinutes(),
                r.getRouteDone(),
                r.getCreatedAt());
    }
}
