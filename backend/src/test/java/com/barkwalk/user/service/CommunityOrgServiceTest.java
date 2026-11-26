package com.barkwalk.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.AuditService;
import com.barkwalk.moderation.repository.ReportRepository;
import com.barkwalk.review.domain.Review;
import com.barkwalk.review.repository.ReviewRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityOrgServiceTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CommunityOrgService communityOrgService;

    @Test
    void onWalkCompleted_marksEligibleAtThreshold() {
        Profile profile = new Profile();
        profile.setCompletedWalksCount(2);
        profile.setCommunityOrgStatus("NONE");
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);
        when(businessSettingsService.orgMaxReports()).thenReturn(2);
        when(businessSettingsService.orgMinReviews()).thenReturn(5);
        when(reportRepository.countByTargetTypeAndTargetIdAndDecision("USER", 5L, "VALIDATED")).thenReturn(0L);
        when(reviewRepository.findByTargetUserId(5L)).thenReturn(List.of());
        when(profileRepository.save(profile)).thenReturn(profile);

        communityOrgService.onWalkCompleted(5L);

        assertThat(profile.getCompletedWalksCount()).isEqualTo(3);
        assertThat(profile.getCommunityOrgStatus()).isEqualTo("ELIGIBLE");
    }

    @Test
    void evaluateRevocation_revokesOnValidatedReports() {
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("ACTIVE");
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(reportRepository.countByTargetTypeAndTargetIdAndDecision("USER", 5L, "VALIDATED")).thenReturn(2L);
        when(businessSettingsService.orgMaxReports()).thenReturn(2);
        when(businessSettingsService.orgMinReviews()).thenReturn(5);
        when(reviewRepository.findByTargetUserId(5L)).thenReturn(List.of());
        when(profileRepository.save(profile)).thenReturn(profile);
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        communityOrgService.evaluateRevocation(5L);

        assertThat(profile.getCommunityOrgStatus()).isEqualTo("REVOKED");
        verify(auditService).log(null, "ORG_REVOKED", "USER", 5L, "reports");
    }

    @Test
    void evaluateRevocation_revokesOnLowRating() {
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("ACTIVE");
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(reportRepository.countByTargetTypeAndTargetIdAndDecision("USER", 5L, "VALIDATED")).thenReturn(0L);
        when(businessSettingsService.orgMaxReports()).thenReturn(2);
        when(businessSettingsService.orgMinReviews()).thenReturn(5);
        when(businessSettingsService.orgMinRating()).thenReturn(3);
        Review r1 = review(2);
        Review r2 = review(2);
        Review r3 = review(2);
        Review r4 = review(2);
        Review r5 = review(2);
        when(reviewRepository.findByTargetUserId(5L)).thenReturn(List.of(r1, r2, r3, r4, r5));
        when(profileRepository.save(profile)).thenReturn(profile);
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        communityOrgService.evaluateRevocation(5L);

        assertThat(profile.getCommunityOrgStatus()).isEqualTo("REVOKED");
        verify(auditService).log(null, "ORG_REVOKED", "USER", 5L, "rating");
    }

    private static Review review(int rating) {
        Review r = new Review();
        r.setRating(rating);
        return r;
    }
}
