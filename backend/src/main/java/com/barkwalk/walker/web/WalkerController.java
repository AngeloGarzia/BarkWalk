package com.barkwalk.walker.web;

import com.barkwalk.walker.dto.WalkerDtos.ApplyRequest;
import com.barkwalk.walker.dto.WalkerDtos.TodayWalkResponse;
import com.barkwalk.walker.dto.WalkerDtos.WalkerProfileResponse;
import com.barkwalk.walker.service.WalkerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/walkers")
public class WalkerController {
    private final WalkerService walkerService;

    public WalkerController(WalkerService walkerService) {
        this.walkerService = walkerService;
    }

    @PostMapping("/apply")
    public WalkerProfileResponse apply(@Valid @RequestBody ApplyRequest request) {
        return walkerService.apply(request);
    }

    @GetMapping("/today")
    public List<TodayWalkResponse> today() {
        return walkerService.today();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WalkerProfileResponse> pending() {
        return walkerService.pending();
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public WalkerProfileResponse validate(@PathVariable Long id) {
        return walkerService.validate(id);
    }
}
