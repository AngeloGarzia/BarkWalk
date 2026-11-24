package com.barkwalk.walk.repository;

import com.barkwalk.walk.domain.MeetingPoint;
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

public interface MeetingPointRepository extends JpaRepository<MeetingPoint, Long> {
    List<MeetingPoint> findByCityIdAndActiveTrue(Long cityId);
}
