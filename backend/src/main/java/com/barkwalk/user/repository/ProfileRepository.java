package com.barkwalk.user.repository;

import com.barkwalk.user.domain.Profile;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
// Accès profils.

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p where p.userId = :userId")
    Optional<Profile> findByIdForUpdate(@Param("userId") Long userId);

    List<Profile> findByReviewStatusOrderByUpdatedAtAsc(String reviewStatus);

    @Query("""
            SELECT COUNT(p) > 0 FROM Profile p
            WHERE lower(p.pseudo) = lower(:pseudo)
              AND (:excludeUserId IS NULL OR p.userId <> :excludeUserId)
            """)
    boolean existsPseudoIgnoreCase(
            @Param("pseudo") String pseudo, @Param("excludeUserId") Long excludeUserId);
}
