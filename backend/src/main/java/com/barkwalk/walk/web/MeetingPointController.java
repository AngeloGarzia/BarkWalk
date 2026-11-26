package com.barkwalk.walk.web;

import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointCreateRequest;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointResponse;
import com.barkwalk.walk.service.MeetingPointService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Points de rendez-vous (liste + création depuis la carte).
@RestController
@RequestMapping("/api/meeting-points")
public class MeetingPointController {
    private final MeetingPointService meetingPointService;

    public MeetingPointController(MeetingPointService meetingPointService) {
        this.meetingPointService = meetingPointService;
    }

    @GetMapping
    public List<MeetingPointResponse> list(@RequestParam Long cityId) {
        return meetingPointService.listByCity(cityId);
    }

    @PostMapping
    public MeetingPointResponse create(@Valid @RequestBody MeetingPointCreateRequest request) {
        return meetingPointService.create(request);
    }
}
