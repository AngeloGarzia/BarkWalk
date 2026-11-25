package com.barkwalk.attendance.web;

import com.barkwalk.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.ReportRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.WalkReportResponse;
import com.barkwalk.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
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
