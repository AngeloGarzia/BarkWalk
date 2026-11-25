package com.barkwalk.attendance.service;

import com.barkwalk.attendance.domain.Attendance;
import com.barkwalk.attendance.domain.WalkReport;
import com.barkwalk.attendance.domain.WalkReportDogNote;
import com.barkwalk.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.DogNote;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.ReportRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.WalkReportResponse;
import com.barkwalk.attendance.repository.AttendanceRepository;
import com.barkwalk.attendance.repository.WalkReportDogNoteRepository;
import com.barkwalk.attendance.repository.WalkReportRepository;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.Role;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import java.time.Instant;
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
    private final ProfileRepository profileRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            BookingRepository bookingRepository,
            WalkReportRepository walkReportRepository,
            WalkReportDogNoteRepository noteRepository,
            ProfileRepository profileRepository) {
        this.attendanceRepository = attendanceRepository;
        this.bookingRepository = bookingRepository;
        this.walkReportRepository = walkReportRepository;
        this.noteRepository = noteRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public AttendanceResponse departure(PointRequest request) {
        requireWalker();
        return toAttendance(point(request, true));
    }

    @Transactional
    public AttendanceResponse markReturn(PointRequest request) {
        requireWalker();
        Attendance attendance = point(request, false);
        Booking booking = bookingRepository.findById(request.bookingId()).orElseThrow();
        if ("PRESENT".equalsIgnoreCase(request.status())) {
            Profile profile = profileRepository.findById(booking.getUserId()).orElse(null);
            if (profile != null) {
                profile.setCompletedWalksCount(profile.getCompletedWalksCount() + 1);
                if (profile.getCompletedWalksCount() >= 3
                        && !profile.isCommunityOrgRevoked()
                        && !"ACTIVE".equals(profile.getCommunityOrgStatus())) {
                    profile.setCommunityOrgStatus("ELIGIBLE");
                }
            }
            booking.setStatus("COMPLETED");
        }
        return toAttendance(attendance);
    }

    @Transactional
    public WalkReportResponse report(ReportRequest request) {
        requireWalker();
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

    private Attendance point(PointRequest request, boolean departure) {
        Booking booking = bookingRepository
                .findById(request.bookingId())
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
        if (!(roles.contains(Role.WALKER_STAFF)
                || roles.contains(Role.WALKER_INDEP)
                || roles.contains(Role.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
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
