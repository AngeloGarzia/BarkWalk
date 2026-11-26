package com.barkwalk.review.repository;

import com.barkwalk.review.domain.Review;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
// Accès avis.

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByWalkIdAndAuthorIdAndTargetUserId(Long walkId, Long authorId, Long targetUserId);

    List<Review> findByTargetUserId(Long targetUserId);
}
