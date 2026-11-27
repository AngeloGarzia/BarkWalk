package com.barkwalk.attendance.web;

import com.barkwalk.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.PendingRollcallWalk;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.ReportRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.RosterResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.WalkReportResponse;
import com.barkwalk.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/pending")
    public List<PendingRollcallWalk> pending() {
        return attendanceService.pendingRollcalls();
    }

    @GetMapping("/walks/{walkId}/roster")
    public RosterResponse roster(@PathVariable Long walkId) {
        return attendanceService.roster(walkId);
    }

    @PostMapping("/walks/{walkId}/rollcall")
    public RollcallResponse rollcall(@PathVariable Long walkId, @Valid @RequestBody RollcallRequest request) {
        return attendanceService.submitRollcall(walkId, request);
    }

    @PostMapping("/departure")
    public AttendanceResponse departure(@Valid @RequestBody PointRequest request) {
        return attendanceService.departure(request);
    }

    @PostMapping("/return")
    public AttendanceResponse markReturn(@Valid @RequestBody PointRequest request) {
        return attendanceService.markReturn(request);
    }

    @PostMapping("/reports")
    public WalkReportResponse report(@Valid @RequestBody ReportRequest request) {
        return attendanceService.report(request);
    }
}
