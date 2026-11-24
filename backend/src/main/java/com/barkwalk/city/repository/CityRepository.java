package com.barkwalk.city.repository;

import com.barkwalk.city.domain.City;
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

public interface CityRepository extends JpaRepository<City, Long> {
    @Query("""
            SELECT c FROM City c
            WHERE c.active = true AND (
              LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
              OR c.postalCode LIKE CONCAT(:q, '%')
            )
            ORDER BY c.name, c.postalCode
            """)
        List<City> search(@Param("q") String q, Pageable pageable);
}
