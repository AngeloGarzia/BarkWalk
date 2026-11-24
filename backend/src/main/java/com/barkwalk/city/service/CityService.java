package com.barkwalk.city.service;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.domain.Zone;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.city.repository.ZoneRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CityService {
    private final CityRepository cityRepository;
    private final ZoneRepository zoneRepository;

    public CityService(CityRepository cityRepository, ZoneRepository zoneRepository) {
        this.cityRepository = cityRepository;
        this.zoneRepository = zoneRepository;
    }

    @Cacheable(cacheNames = "cities", key = "#q")
    public List<City> search(String q) {
        if (q == null || q.isBlank()) {
            return cityRepository.findAll(PageRequest.of(0, 30)).getContent();
        }
        return cityRepository.search(q.trim(), PageRequest.of(0, 30));
    }

    public List<Zone> zones(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
        }
        return zoneRepository.findByCityIdOrderByNameAsc(cityId);
    }
}
