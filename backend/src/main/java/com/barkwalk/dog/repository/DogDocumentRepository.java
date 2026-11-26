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

    @Query("""
        SELECT d FROM DogDocument d
        WHERE d.status = 'VALIDATED'
          AND d.expiresAt IS NOT NULL
          AND d.expiresAt = :expiresOn
          AND ((:kind = 'J30' AND d.reminderJ30SentAt IS NULL)
            OR (:kind = 'J7' AND d.reminderJ7SentAt IS NULL))
        """)
    List<DogDocument> findDueReminders(
            @Param("expiresOn") java.time.LocalDate expiresOn, @Param("kind") String kind);
}
