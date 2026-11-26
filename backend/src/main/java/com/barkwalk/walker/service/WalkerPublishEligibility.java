package com.barkwalk.walker.service;

import com.barkwalk.walker.domain.WalkerProfile;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// R15 / R16 : indépendant — diplôme, SIRET, RC pro valide, charte ; doc expiré bloque publication.
@Service
public class WalkerPublishEligibility {

    public void assertCanPublish(WalkerProfile profile) {
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Walker profile required");
        }
        if (!"VALIDATED".equals(profile.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Walker not validated");
        }
        if (!"INDEP".equalsIgnoreCase(profile.getWalkerType())
                && !"INDEPENDENT".equalsIgnoreCase(profile.getWalkerType())) {
            return;
        }
        if (isBlank(profile.getSiret())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIRET required to publish");
        }
        if (isBlank(profile.getDiplomaKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diploma required to publish");
        }
        if (isBlank(profile.getRcProKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RC Pro required to publish");
        }
        if (profile.getCharterSignedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Charter signature required to publish");
        }
        if (profile.getRcProExpiresAt() != null && profile.getRcProExpiresAt().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RC Pro expired — renew before publishing");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
