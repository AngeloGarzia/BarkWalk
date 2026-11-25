package com.barkwalk.user.service;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.user.dto.ProfileDtos.ProfileResponse;
import com.barkwalk.user.dto.ProfileDtos.ProfileUpdateRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Lecture et mise à jour du profil de l'utilisateur connecté.
@Service
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProfileService(ProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me() {
        Long id = SecurityUtils.currentUserId();
        User user = userRepository.findById(id).orElseThrow();
        Profile profile = profileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse update(ProfileUpdateRequest request) {
        Long id = SecurityUtils.currentUserId();
        User user = userRepository.findById(id).orElseThrow();
        Profile profile = profileRepository.findById(id).orElseThrow();
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setCityId(request.cityId());
        profile.setZoneId(request.zoneId());
        profile.setUpdatedAt(Instant.now());
        return toResponse(user, profile);
    }

    private ProfileResponse toResponse(User user, Profile profile) {
        return new ProfileResponse(user.getId(), user.getEmail(), profile.getFirstName(), profile.getLastName(),
                profile.getPhone(), profile.getBio(), profile.getCityId(), profile.getZoneId(),
                profile.getCompletedWalksCount(), profile.getCommunityOrgStatus(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
    }
}
