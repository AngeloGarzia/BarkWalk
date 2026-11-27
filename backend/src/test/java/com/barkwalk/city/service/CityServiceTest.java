package com.barkwalk.city.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.domain.Zone;
import com.barkwalk.city.dto.CityDtos.CityResponse;
import com.barkwalk.city.dto.CityDtos.ZoneResponse;
import com.barkwalk.city.geo.GeoApiCommuneClient;
import com.barkwalk.city.geo.GeoApiCommuneClient.GeoCommune;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.city.repository.ZoneRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    @Mock private CityRepository cityRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private GeoApiCommuneClient geoApiCommuneClient;

    @InjectMocks
    private CityService cityService;

    /** Recherche vide : utilise la page locale sans appeler l'API distante. */
    @Test
    void search_blankUsesLocalPage() {
        City paris = city(1L, "75056", "75001", "Paris");
        when(cityRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(paris)));

        List<CityResponse> result = cityService.search("  ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Paris");
    }

    /** Code postal 5 chiffres : interroge l'API geo puis upsert la commune. */
    @Test
    void search_postalFiveDigitsUsesGeoClientThenUpsert() {
        GeoCommune commune = new GeoCommune(
                "Paris",
                "75056",
                List.of("75001"),
                null,
                new GeoApiCommuneClient.GeoNamed("75", "Paris"),
                new GeoApiCommuneClient.GeoNamed("11", "Île-de-France"));

        when(geoApiCommuneClient.searchByPostalCode("75001", 15)).thenReturn(List.of(commune));
        when(cityRepository.findByInseeCodeAndPostalCode("75056", "75001"))
                .thenReturn(Optional.empty());
        when(cityRepository.save(any(City.class))).thenAnswer(inv -> inv.getArgument(0));

        List<CityResponse> result = cityService.search("75001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Paris");
        assertThat(result.get(0).postalCode()).isEqualTo("75001");
    }

    /** Lève NOT_FOUND si la ville est absente du référentiel. */
    @Test
    void zones_throwsNotFoundForMissingCity() {
        when(cityRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> cityService.zones(999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Retourne les zones triées de la commune. */
    @Test
    void zones_mapsRepository() {
        when(cityRepository.existsById(1L)).thenReturn(true);

        Zone zone = mock(Zone.class);
        when(zone.getId()).thenReturn(10L);
        when(zone.getCityId()).thenReturn(1L);
        when(zone.getName()).thenReturn("Centre-ville");

        when(zoneRepository.findByCityIdOrderByNameAsc(1L)).thenReturn(List.of(zone));

        List<ZoneResponse> result = cityService.zones(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Centre-ville");
        assertThat(result.get(0).cityId()).isEqualTo(1L);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static City city(long id, String inseeCode, String postalCode, String name) {
        City city = new City();
        try {
            var f = City.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(city, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        city.setInseeCode(inseeCode);
        city.setPostalCode(postalCode);
        city.setName(name);
        city.setDepartment("75");
        city.setRegion("Île-de-France");
        city.setActive(true);
        return city;
    }
}
