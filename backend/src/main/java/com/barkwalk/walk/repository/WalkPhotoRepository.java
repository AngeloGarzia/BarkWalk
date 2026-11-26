package com.barkwalk.walk.repository;

import com.barkwalk.walk.domain.WalkPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkPhotoRepository extends JpaRepository<WalkPhoto, Long> {
    List<WalkPhoto> findByWalkIdOrderByKindAscSortOrderAsc(Long walkId);

    List<WalkPhoto> findByWalkIdAndKindOrderBySortOrderAsc(Long walkId, String kind);

    long countByWalkIdAndKind(Long walkId, String kind);

    Optional<WalkPhoto> findByIdAndWalkId(Long id, Long walkId);

    List<WalkPhoto> findByWalkIdInOrderByWalkIdAscKindAscSortOrderAsc(List<Long> walkIds);
}
