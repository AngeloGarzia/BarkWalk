package com.barkwalk.user.web;

import com.barkwalk.user.service.ProfileService;
import com.barkwalk.user.dto.ProfileDtos.ProfileResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

// API profil.
@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    @GetMapping("/profile")
    public ProfileResponse me() { return profileService.me(); }

    @PutMapping("/profile")
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(request);
    }
}
