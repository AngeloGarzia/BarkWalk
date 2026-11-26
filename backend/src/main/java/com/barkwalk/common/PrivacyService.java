package com.barkwalk.common;

import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Visibilité téléphone / libellé public après suppression (règles étape 2).
@Service
public class PrivacyService {
    public static final String ANONYMIZED_DISPLAY_NAME = "Membre supprimé";

    private static final List<String> ACTIVE_BOOKING = List.of("CONFIRMED", "PENDING_PAYMENT", "COMPLETED");

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final WalkerProfileRepository walkerProfileRepository;

    public PrivacyService(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            WalkRepository walkRepository,
            BookingRepository bookingRepository,
            WalkerProfileRepository walkerProfileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.walkerProfileRepository = walkerProfileRepository;
    }

    @Transactional(readOnly = true)
    public String displayName(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            return ANONYMIZED_DISPLAY_NAME;
        }
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            return ANONYMIZED_DISPLAY_NAME;
        }
        String first = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String last = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? ANONYMIZED_DISPLAY_NAME : name;
    }

    /**
     * Téléphone visible uniquement pour soi, l'admin, ou l'encadrant/organisateur
     * d'une balade où la cible a une réservation active.
     */
    @Transactional(readOnly = true)
    public boolean canViewPhone(Long viewerId, Long targetUserId, Long walkId) {
        if (viewerId == null || targetUserId == null) {
            return false;
        }
        if (viewerId.equals(targetUserId)) {
            return true;
        }
        var principal = SecurityUtils.currentUser();
        if (principal.getRoles().contains(Role.ADMIN)) {
            return true;
        }
        if (walkId == null) {
            return false;
        }
        Walk walk = walkRepository.findById(walkId).orElse(null);
        if (walk == null) {
            return false;
        }
        boolean targetBooked = bookingRepository.findByWalkId(walkId).stream()
                .anyMatch(b -> targetUserId.equals(b.getUserId()) && ACTIVE_BOOKING.contains(b.getStatus()));
        if (!targetBooked) {
            return false;
        }
        if (walk.getOrganizerUserId() != null && walk.getOrganizerUserId().equals(viewerId)) {
            return true;
        }
        if (walk.getWalkerId() == null) {
            return false;
        }
        Optional<WalkerProfile> walker = walkerProfileRepository.findByUserId(viewerId);
        return walker.isPresent() && walk.getWalkerId().equals(walker.get().getId());
    }

    @Transactional(readOnly = true)
    public String phoneForViewer(Long viewerId, Long targetUserId, Long walkId) {
        if (!canViewPhone(viewerId, targetUserId, walkId)) {
            return null;
        }
        return profileRepository.findById(targetUserId).map(Profile::getPhone).orElse(null);
    }
}
