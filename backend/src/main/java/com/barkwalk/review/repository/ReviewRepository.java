package com.barkwalk.review.repository;

import com.barkwalk.review.domain.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès avis.

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByWalkIdAndAuthorIdAndTargetUserId(Long walkId, Long authorId, Long targetUserId);

    List<Review> findByTargetUserId(Long targetUserId);
}
