package com.barkwalk.review.repository;

import com.barkwalk.review.domain.WalkRating;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkRatingRepository extends JpaRepository<WalkRating, Long> {
    boolean existsByWalkIdAndUserId(Long walkId, Long userId);

    Optional<WalkRating> findByWalkIdAndUserId(Long walkId, Long userId);

    List<WalkRating> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WalkRating> findByWalkIdOrderByCreatedAtDesc(Long walkId);

    List<WalkRating> findByWalkIdInOrderByCreatedAtDesc(List<Long> walkIds);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM WalkRating r WHERE r.walkId = :walkId")
    Double averageForWalk(@Param("walkId") Long walkId);

    long countByWalkId(Long walkId);

    @Query("""
            SELECT COALESCE(AVG(r.rating), 0) FROM WalkRating r
            WHERE r.walkId IN (
              SELECT w.id FROM Walk w
              WHERE w.organizerUserId = :userId
                 OR (:walkerId IS NOT NULL AND w.walkerId = :walkerId)
            )
            """)
    Double averageForCreator(@Param("userId") Long userId, @Param("walkerId") Long walkerId);

    @Query("""
            SELECT COUNT(r) FROM WalkRating r
            WHERE r.walkId IN (
              SELECT w.id FROM Walk w
              WHERE w.organizerUserId = :userId
                 OR (:walkerId IS NOT NULL AND w.walkerId = :walkerId)
            )
            """)
    long countForCreator(@Param("userId") Long userId, @Param("walkerId") Long walkerId);

    void deleteByWalkId(Long walkId);
}
