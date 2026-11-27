package com.barkwalk.user.repository;

import com.barkwalk.user.domain.RefreshToken;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Recherche de refresh tokens non révoqués.

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(java.util.UUID familyId);

    void deleteByUserId(Long userId);
}
