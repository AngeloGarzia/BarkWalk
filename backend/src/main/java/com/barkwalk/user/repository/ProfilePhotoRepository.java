package com.barkwalk.user.repository;

import com.barkwalk.user.domain.ProfilePhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {
    List<ProfilePhoto> findByUserIdOrderBySortOrderAsc(Long userId);

    long countByUserId(Long userId);

    Optional<ProfilePhoto> findByIdAndUserId(Long id, Long userId);
}
