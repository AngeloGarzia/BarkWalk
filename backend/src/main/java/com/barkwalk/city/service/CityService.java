package com.barkwalk.city.service;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.domain.Zone;
import com.barkwalk.city.dto.CityDtos.CityResponse;
import com.barkwalk.city.dto.CityDtos.ZoneResponse;
import com.barkwalk.city.geo.GeoApiCommuneClient;
import com.barkwalk.city.geo.GeoApiCommuneClient.GeoCommune;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.city.repository.ZoneRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Recherche de communes françaises par nom ou code postal (geo.api.gouv.fr + cache local).
@Service
public class CityService {
    private static final int RESULT_LIMIT = 30;
    private static final int REMOTE_LIMIT = 15;

    private final CityRepository cityRepository;
    private final ZoneRepository zoneRepository;
    private final GeoApiCommuneClient geoApiCommuneClient;

    public CityService(
            CityRepository cityRepository,
            ZoneRepository zoneRepository,
            GeoApiCommuneClient geoApiCommuneClient) {
        this.cityRepository = cityRepository;
        this.zoneRepository = zoneRepository;
        this.geoApiCommuneClient = geoApiCommuneClient;
    }

    @Transactional
    @Cacheable(cacheNames = "cities", key = "#q == null ? '' : #q.trim().toLowerCase()")
    public List<CityResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return cityRepository.findAll(PageRequest.of(0, RESULT_LIMIT)).getContent().stream()
                    .map(this::toCity)
                    .toList();
        }

        String query = q.trim();
        List<GeoCommune> remote;
        if (isPostalQuery(query)) {
            remote = query.length() == 5
                    ? geoApiCommuneClient.searchByPostalCode(query, REMOTE_LIMIT)
                    : List.of();
        } else {
            remote = geoApiCommuneClient.searchByName(query, REMOTE_LIMIT);
        }

        if (!remote.isEmpty()) {
            return expandAndUpsert(remote, query);
        }

        // Préfixe CP (2–4 chiffres) ou repli si l’API distante est indisponible.
        return cityRepository.search(query, PageRequest.of(0, RESULT_LIMIT)).stream()
                .map(this::toCity)
                .toList();
    }

    public List<ZoneResponse> zones(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
        }
        return zoneRepository.findByCityIdOrderByNameAsc(cityId).stream().map(this::toZone).toList();
    }

    private List<CityResponse> expandAndUpsert(List<GeoCommune> remote, String query) {
        Map<String, CityResponse> byKey = new LinkedHashMap<>();
        boolean postalPrefix = isPostalQuery(query);

        for (GeoCommune commune : remote) {
            if (commune == null || commune.code() == null || commune.nom() == null) {
                continue;
            }
            List<String> postalCodes = commune.codesPostaux() == null ? List.of() : commune.codesPostaux();
            for (String postalCode : postalCodes) {
                if (postalCode == null || postalCode.isBlank()) {
                    continue;
                }
                if (postalPrefix && !postalCode.startsWith(query)) {
                    continue;
                }
                City city = upsert(commune, postalCode);
                String key = city.getInseeCode() + "|" + city.getPostalCode();
                byKey.putIfAbsent(key, toCity(city));
                if (byKey.size() >= RESULT_LIMIT) {
                    return new ArrayList<>(byKey.values());
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private City upsert(GeoCommune commune, String postalCode) {
        return cityRepository
                .findByInseeCodeAndPostalCode(commune.code(), postalCode)
                .orElseGet(() -> cityRepository.save(fromGeo(commune, postalCode)));
    }

    private static City fromGeo(GeoCommune commune, String postalCode) {
        City city = new City();
        city.setInseeCode(commune.code());
        city.setPostalCode(postalCode);
        city.setName(commune.nom());
        city.setDepartment(
                commune.departement() != null && commune.departement().code() != null
                        ? commune.departement().code()
                        : "?");
        city.setRegion(
                commune.region() != null && commune.region().nom() != null
                        ? commune.region().nom()
                        : "?");
        if (commune.centre() != null
                && commune.centre().coordinates() != null
                && commune.centre().coordinates().size() >= 2) {
            city.setLng(commune.centre().coordinates().get(0));
            city.setLat(commune.centre().coordinates().get(1));
        }
        city.setActive(true);
        return city;
    }

    private static boolean isPostalQuery(String query) {
        if (query.length() < 2 || query.length() > 5) {
            return false;
        }
        for (int i = 0; i < query.length(); i++) {
            if (!Character.isDigit(query.charAt(i))) {
                return false;
            }
        }
        return true;
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
