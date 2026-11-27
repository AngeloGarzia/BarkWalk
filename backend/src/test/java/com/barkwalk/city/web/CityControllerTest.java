package com.barkwalk.city.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.city.dto.CityDtos.CityResponse;
import com.barkwalk.city.dto.CityDtos.ZoneResponse;
import com.barkwalk.city.service.CityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CityControllerTest {

    @Mock
    private CityService cityService;

    @InjectMocks
    private CityController cityController;

    // Recherche les communes selon la saisie utilisateur.
    @Test
    void search_delegatesToService() {
        List<CityResponse> cities = List.of();
        when(cityService.search("Lyon")).thenReturn(cities);
        assertThat(cityController.search("Lyon")).isSameAs(cities);
        verify(cityService).search("Lyon");
    }

    // Liste les zones rattachées à une commune.
    @Test
    void zones_delegatesToService() {
        List<ZoneResponse> zones = List.of();
        when(cityService.zones(12L)).thenReturn(zones);
        assertThat(cityController.zones(12L)).isSameAs(zones);
    }
}
