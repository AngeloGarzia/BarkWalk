package com.barkwalk.dog.repository;

import com.barkwalk.dog.domain.DogDocument;
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
// Documents rattachés à un chien.

public interface DogDocumentRepository extends JpaRepository<DogDocument, Long> {
    List<DogDocument> findByDogId(Long dogId);
}
