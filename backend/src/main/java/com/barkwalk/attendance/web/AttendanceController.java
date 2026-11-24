package com.barkwalk.attendance.web;

import com.barkwalk.attendance.domain.Attendance;
import com.barkwalk.attendance.domain.WalkReport;
import com.barkwalk.attendance.domain.WalkReportDogNote;
import com.barkwalk.attendance.repository.AttendanceRepository;
import com.barkwalk.attendance.repository.WalkReportDogNoteRepository;
import com.barkwalk.attendance.repository.WalkReportRepository;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceRepository attendanceRepository;
    private final BookingRepository bookingRepository;
    private final WalkReportRepository walkReportRepository;
    private final WalkReportDogNoteRepository noteRepository;
    private final ProfileRepository profileRepository;

    public AttendanceController(AttendanceRepository attendanceRepository, BookingRepository bookingRepository,
                                WalkReportRepository walkReportRepository, WalkReportDogNoteRepository noteRepository,
                                ProfileRepository profileRepository) {
        this.attendanceRepository = attendanceRepository;
        this.bookingRepository = bookingRepository;
        this.walkReportRepository = walkReportRepository;
        this.noteRepository = noteRepository;
        this.profileRepository = profileRepository;
    }

    public record PointRequest(@NotNull Long bookingId, @NotBlank String status) {}
    public record ReportRequest(@NotNull Long walkId, Integer actualDurationMinutes, String routeDone,
                                String incidentType, String incidentDescription, String incidentSeverity,
                                List<DogNote> dogNotes) {}
    public record DogNote(@NotNull Long dogId, Integer behaviorScore, String comment) {}

    @PostMapping("/departure")
    @Transactional
    public Attendance departure(@RequestBody PointRequest request) {
        requireWalker();
        return point(request, true);
    }

    @PostMapping("/return")
    @Transactional
    public Attendance markReturn(@RequestBody PointRequest request) {
        requireWalker();
        Attendance attendance = point(request, false);
        Booking booking = bookingRepository.findById(request.bookingId()).orElseThrow();
        if ("PRESENT".equalsIgnoreCase(request.status())) {
            Profile profile = profileRepository.findById(booking.getUserId()).orElse(null);
            if (profile != null) {
                profile.setCompletedWalksCount(profile.getCompletedWalksCount() + 1);
                if (profile.getCompletedWalksCount() >= 3 && !profile.isCommunityOrgRevoked()) {
                    profile.setCommunityOrgEnabled(true);
                }
            }
            booking.setStatus("COMPLETED");
        }
        return attendance;
    }

    @PostMapping("/reports")
    @Transactional
    public WalkReport report(@RequestBody ReportRequest request) {
        requireWalker();
        WalkReport report = walkReportRepository.findByWalkId(request.walkId()).orElseGet(WalkReport::new);
        report.setWalkId(request.walkId());
        report.setAuthorId(SecurityUtils.currentUserId());
        report.setActualDurationMinutes(request.actualDurationMinutes());
        report.setRouteDone(request.routeDone());
        report.setIncidentType(request.incidentType());
        report.setIncidentDescription(request.incidentDescription());
        report.setIncidentSeverity(request.incidentSeverity());
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
        return report;
    }

    private Attendance point(PointRequest request, boolean departure) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Attendance attendance = attendanceRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            Attendance a = new Attendance();
            a.setBookingId(booking.getId());
            return a;
        });
        if (departure) {
            attendance.setDepartureStatus(request.status());
            attendance.setDepartureAt(Instant.now());
        } else {
            attendance.setReturnStatus(request.status());
            attendance.setReturnAt(Instant.now());
        }
        return attendanceRepository.save(attendance);
    }

    private void requireWalker() {
        var roles = SecurityUtils.currentUser().getRoles();
        if (!(roles.contains(Role.WALKER_STAFF) || roles.contains(Role.WALKER_INDEP) || roles.contains(Role.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
