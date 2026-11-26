package com.barkwalk.user.web;

import com.barkwalk.user.dto.ProfileDtos.ProfileResponse;
import com.barkwalk.user.dto.ProfileDtos.RefuseRequest;
import com.barkwalk.user.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Validation administrative des fiches profil (identité / ville / téléphone).
@RestController
@RequestMapping("/api/admin/profiles")
public class AdminProfileController {
    private final ProfileService profileService;

    public AdminProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/pending")
    public List<ProfileResponse> pending() {
        return profileService.pendingReviews();
    }

    @PostMapping("/{userId}/validate")
    public ProfileResponse validate(@PathVariable Long userId) {
        return profileService.validateReview(userId);
    }

    @PostMapping("/{userId}/refuse")
    public ProfileResponse refuse(@PathVariable Long userId, @Valid @RequestBody RefuseRequest request) {
        return profileService.refuseReview(userId, request.reason());
    }
}
