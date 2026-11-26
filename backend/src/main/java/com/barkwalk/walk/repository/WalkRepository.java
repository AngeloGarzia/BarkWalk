package com.barkwalk.walk.repository;

import com.barkwalk.walk.domain.Walk;
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
// Requêtes catalogue et verrou pessimiste pour la réservation.

public interface WalkRepository extends JpaRepository<Walk, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT w FROM Walk w WHERE w.id = :id")
        Optional<Walk> findByIdForUpdate(@Param("id") Long id);
    
        @Query("""
            SELECT w FROM Walk w WHERE w.status = 'PUBLISHED'
            AND w.archivedAt IS NULL
            AND (:cityId IS NULL OR w.cityId = :cityId)
            AND (:type IS NULL OR w.walkType = :type)
            AND w.startsAt >= :from
            ORDER BY w.startsAt ASC
            """)
        List<Walk> catalogue(@Param("cityId") Long cityId, @Param("type") String type, @Param("from") Instant from);

    @Query("""
            SELECT w FROM Walk w
            WHERE w.archivedAt IS NOT NULL
            ORDER BY w.archivedAt DESC
            """)
    List<Walk> findArchived();

    @Query("""
            SELECT w FROM Walk w
            WHERE w.archivedAt IS NOT NULL
              AND (
                :cityId IS NULL
                OR w.cityId = :cityId
                OR EXISTS (
                  SELECT 1 FROM City cSel, City cWalk
                  WHERE cSel.id = :cityId
                    AND cWalk.id = w.cityId
                    AND lower(cSel.name) = lower(cWalk.name)
                )
              )
            ORDER BY w.archivedAt DESC
            """)
    List<Walk> findArchivedFiltered(@Param("cityId") Long cityId);

    /** Admin catalogue : toutes les balades, filtre ville / statut optionnels. */
    @Query("""
            SELECT w FROM Walk w
            WHERE (
                :cityId IS NULL
                OR w.cityId = :cityId
                OR EXISTS (
                  SELECT 1 FROM City cSel, City cWalk
                  WHERE cSel.id = :cityId
                    AND cWalk.id = w.cityId
                    AND lower(cSel.name) = lower(cWalk.name)
                )
              )
              AND (
                :status IS NULL
                OR (:status = 'ARCHIVED' AND w.archivedAt IS NOT NULL)
                OR (:status <> 'ARCHIVED' AND w.status = :status)
              )
            ORDER BY w.startsAt DESC
            """)
    List<Walk> findAdminOverview(@Param("cityId") Long cityId, @Param("status") String status);

    @Query("""
        SELECT w FROM Walk w
        WHERE w.walkerId = :walkerId
          AND w.startsAt >= :from
          AND w.startsAt < :to
          AND w.status <> 'CANCELLED'
        ORDER BY w.startsAt ASC
        """)
    List<Walk> findForWalkerBetween(
            @Param("walkerId") Long walkerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        SELECT w FROM Walk w
        WHERE w.status = 'PUBLISHED'
          AND w.archivedAt IS NULL
          AND w.startsAt >= :from
          AND w.startsAt < :to
        ORDER BY w.startsAt ASC
        """)
    List<Walk> findPublishedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        SELECT w FROM Walk w
        WHERE w.organizerUserId = :userId
           OR (:walkerId IS NOT NULL AND w.walkerId = :walkerId)
        ORDER BY w.startsAt DESC
        """)
    List<Walk> findMine(@Param("userId") Long userId, @Param("walkerId") Long walkerId);
}
