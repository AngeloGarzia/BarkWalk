package com.barkwalk.dog.repository;

import com.barkwalk.dog.domain.Dog;
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
// Accès aux chiens d'un propriétaire.

public interface DogRepository extends JpaRepository<Dog, Long> {
    List<Dog> findByOwnerIdOrderByNameAsc(Long ownerId);

    List<Dog> findByDossierStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dog d where d.id = :id")
    Optional<Dog> findByIdForUpdate(@Param("id") Long id);
}
