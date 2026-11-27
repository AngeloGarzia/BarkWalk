package com.barkwalk.city.repository;

import com.barkwalk.city.domain.Zone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
// Accès données du module city. Référentiel géographique.

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByCityIdOrderByNameAsc(Long cityId);
}
