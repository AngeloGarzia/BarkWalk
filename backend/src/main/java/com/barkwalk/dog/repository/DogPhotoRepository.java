package com.barkwalk.dog.repository;

import com.barkwalk.dog.domain.DogPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogPhotoRepository extends JpaRepository<DogPhoto, Long> {
    List<DogPhoto> findByDogIdOrderBySortOrderAsc(Long dogId);

    long countByDogId(Long dogId);

    Optional<DogPhoto> findByIdAndDogId(Long id, Long dogId);

    void deleteByDogId(Long dogId);
}
