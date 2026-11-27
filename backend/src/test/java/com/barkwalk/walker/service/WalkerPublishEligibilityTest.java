package com.barkwalk.walker.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barkwalk.walker.domain.WalkerProfile;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WalkerPublishEligibilityTest {

    private final WalkerPublishEligibility eligibility = new WalkerPublishEligibility();

    // Autorise la publication d'un profil indépendant complet.
    @Test
    void assertCanPublish_allowsCompleteIndep() {
        WalkerProfile profile = completeIndep();
        assertThatCode(() -> eligibility.assertCanPublish(profile)).doesNotThrowAnyException();
    }

    // Refuse si le SIRET manque.
    @Test
    void assertCanPublish_rejectsMissingSiret() {
        WalkerProfile profile = completeIndep();
        profile.setSiret(null);
        assertThatThrownBy(() -> eligibility.assertCanPublish(profile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // Refuse si RC Pro expirée.
    @Test
    void assertCanPublish_rejectsExpiredRcPro() {
        WalkerProfile profile = completeIndep();
        profile.setRcProExpiresAt(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> eligibility.assertCanPublish(profile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((ResponseStatusException) ex).getReason())
                        .contains("RC Pro expired"));
    }

    // Passe la vérification pour un marcheur staff.
    @Test
    void assertCanPublish_skipsStaff() {
        WalkerProfile profile = new WalkerProfile();
        profile.setWalkerType("STAFF");
        profile.setStatus("VALIDATED");
        assertThatCode(() -> eligibility.assertCanPublish(profile)).doesNotThrowAnyException();
    }

    private static WalkerProfile completeIndep() {
        WalkerProfile profile = new WalkerProfile();
        profile.setWalkerType("INDEP");
        profile.setStatus("VALIDATED");
        profile.setSiret("12345678900012");
        profile.setDiplomaKey("walkers/1/diploma.pdf");
        profile.setRcProKey("walkers/1/rcpro.pdf");
        profile.setRcProExpiresAt(LocalDate.now().plusYears(1));
        profile.setCharterSignedAt(Instant.now());
        return profile;
    }
}
