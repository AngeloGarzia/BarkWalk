package com.barkwalk.walk.repository;

import com.barkwalk.walk.domain.MeetingPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès aux points de rendez-vous actifs par ville.

public interface MeetingPointRepository extends JpaRepository<MeetingPoint, Long> {
    List<MeetingPoint> findByCityIdAndActiveTrue(Long cityId);
}
