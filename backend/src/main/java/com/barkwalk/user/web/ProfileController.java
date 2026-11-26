package com.barkwalk.user.web;

import com.barkwalk.user.dto.ProfileDtos.PhotoResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileUpdateRequest;
import com.barkwalk.user.service.AccountDeletionService;
import com.barkwalk.user.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// API profil + photos (1–3) + suppression compte.
@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final ProfileService profileService;
    private final AccountDeletionService accountDeletionService;

    public ProfileController(ProfileService profileService, AccountDeletionService accountDeletionService) {
        this.profileService = profileService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/profile")
    public ProfileResponse me() {
        return profileService.me();
    }

    @PutMapping("/profile")
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(request);
    }

    @GetMapping("/photos")
    public List<PhotoResponse> photos() {
        return profileService.listPhotos();
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse uploadPhoto(@RequestPart("file") MultipartFile file) {
        return profileService.uploadPhoto(file);
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable Long photoId) {
        profileService.deletePhoto(photoId);
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount() {
        accountDeletionService.deleteMyAccount();
    }
}
