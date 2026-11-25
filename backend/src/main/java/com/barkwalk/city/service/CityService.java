package com.barkwalk.city.service;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.domain.Zone;
import com.barkwalk.city.dto.CityDtos.CityResponse;
import com.barkwalk.city.dto.CityDtos.ZoneResponse;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.city.repository.ZoneRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Recherche de communes françaises par nom ou code postal.
@Service
public class CityService {
    private final CityRepository cityRepository;
    private final ZoneRepository zoneRepository;

    public CityService(CityRepository cityRepository, ZoneRepository zoneRepository) {
        this.cityRepository = cityRepository;
        this.zoneRepository = zoneRepository;
    }

    @Cacheable(cacheNames = "cities", key = "#q")
    public List<CityResponse> search(String q) {
        List<City> cities;
        if (q == null || q.isBlank()) {
            cities = cityRepository.findAll(PageRequest.of(0, 30)).getContent();
        } else {
            cities = cityRepository.search(q.trim(), PageRequest.of(0, 30));
        }
        return cities.stream().map(this::toCity).toList();
    }

    public List<ZoneResponse> zones(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
        }
        return zoneRepository.findByCityIdOrderByNameAsc(cityId).stream().map(this::toZone).toList();
    }

    private CityResponse toCity(City c) {
        return new CityResponse(
                c.getId(),
                c.getInseeCode(),
                c.getPostalCode(),
                c.getName(),
                c.getDepartment(),
                c.getRegion(),
                c.getLat(),
                c.getLng(),
                c.isActive());
    }

    private ZoneResponse toZone(Zone z) {
        return new ZoneResponse(z.getId(), z.getCityId(), z.getName());
    }
}
