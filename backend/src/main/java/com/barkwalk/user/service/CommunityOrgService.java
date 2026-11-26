package com.barkwalk.user.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.Role;
import com.barkwalk.moderation.repository.ReportRepository;
import com.barkwalk.review.repository.ReviewRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// R8 / R10 : éligibilité organisateur communautaire et retrait selon settings.
@Service
public class CommunityOrgService {
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final BusinessSettingsService businessSettingsService;
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final AuditService auditService;

    public CommunityOrgService(
            ProfileRepository profileRepository,
            UserRepository userRepository,
            BusinessSettingsService businessSettingsService,
            ReportRepository reportRepository,
            ReviewRepository reviewRepository,
            AuditService auditService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.businessSettingsService = businessSettingsService;
        this.reportRepository = reportRepository;
        this.reviewRepository = reviewRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void onWalkCompleted(Long userId) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null || profile.isCommunityOrgRevoked()) {
            return;
        }
        profile.setCompletedWalksCount(profile.getCompletedWalksCount() + 1);
        int threshold = businessSettingsService.communityWalksThreshold();
        if (profile.getCompletedWalksCount() >= threshold
                && !"ACTIVE".equals(profile.getCommunityOrgStatus())) {
            profile.setCommunityOrgStatus("ELIGIBLE");
        }
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
        evaluateRevocation(userId);
    }

    @Transactional
    public void evaluateRevocation(Long userId) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null || profile.isCommunityOrgRevoked()) {
            return;
        }
        if (!"ACTIVE".equals(profile.getCommunityOrgStatus())
                && !"ELIGIBLE".equals(profile.getCommunityOrgStatus())) {
            return;
        }
        long validatedReports = reportRepository.countByTargetTypeAndTargetIdAndDecision(
                "USER", userId, "VALIDATED");
        int maxReports = businessSettingsService.orgMaxReports();
        boolean reportsExceeded = maxReports > 0 && validatedReports >= maxReports;

        boolean ratingTooLow = false;
        int minReviews = businessSettingsService.orgMinReviews();
        List<Double> ratings = reviewRepository.findByTargetUserId(userId).stream()
                .map(r -> (double) r.getRating())
                .toList();
        if (ratings.size() >= minReviews) {
            double avg = ratings.stream().mapToDouble(Double::doubleValue).average().orElse(5.0);
            ratingTooLow = avg < businessSettingsService.orgMinRating();
        }

        if (reportsExceeded || ratingTooLow) {
            profile.setCommunityOrgStatus("REVOKED");
            profile.setUpdatedAt(Instant.now());
            profileRepository.save(profile);
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getRoles().contains(Role.COMMUNITY_ORG)) {
                user.getRoles().remove(Role.COMMUNITY_ORG);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            }
            auditService.log(null, "ORG_REVOKED", "USER", userId,
                    reportsExceeded ? "reports" : "rating");
        }
    }
}
