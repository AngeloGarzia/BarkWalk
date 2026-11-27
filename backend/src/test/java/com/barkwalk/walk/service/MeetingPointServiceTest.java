package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointCreateRequest;
import com.barkwalk.walk.dto.MeetingPointDtos.MeetingPointResponse;
import com.barkwalk.walk.repository.MeetingPointRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MeetingPointServiceTest {

    @Mock private MeetingPointRepository meetingPointRepository;
    @Mock private CityRepository cityRepository;

    @InjectMocks
    private MeetingPointService meetingPointService;

    /** Retourne les points actifs de la commune sous forme de DTO. */
    @Test
    void listByCity_mapsRepositoryRows() {
        MeetingPoint mp = meetingPoint(1L, 5L, "Parc Tête d'Or", "1 Av. du Général Eisenhower");
        when(meetingPointRepository.findByCityIdAndActiveTrue(5L)).thenReturn(List.of(mp));

        List<MeetingPointResponse> result = meetingPointService.listByCity(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).cityId()).isEqualTo(5L);
        assertThat(result.get(0).name()).isEqualTo("Parc Tête d'Or");
    }

    /** Rejette la création si la commune est inconnue. */
    @Test
    void create_rejectsUnknownCity() {
        when(cityRepository.existsById(99L)).thenReturn(false);

        MeetingPointCreateRequest request =
                new MeetingPointCreateRequest(99L, "Point test", "Rue inconnue", 48.8, 2.3);

        assertThatThrownBy(() -> meetingPointService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Persiste le nom et l'adresse après suppression des espaces en tête et queue. */
    @Test
    void create_persistsTrimmedNameAndAddress() {
        when(cityRepository.existsById(2L)).thenReturn(true);
        when(meetingPointRepository.save(any(MeetingPoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MeetingPointCreateRequest request =
                new MeetingPointCreateRequest(2L, "  Jardin public  ", "  12 Rue de la Paix  ", 45.7, 4.8);

        MeetingPointResponse response = meetingPointService.create(request);

        assertThat(response.name()).isEqualTo("Jardin public");
        assertThat(response.address()).isEqualTo("12 Rue de la Paix");
        assertThat(response.cityId()).isEqualTo(2L);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static MeetingPoint meetingPoint(long id, long cityId, String name, String address) {
        MeetingPoint mp = new MeetingPoint();
        try {
            var f = MeetingPoint.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(mp, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        mp.setCityId(cityId);
        mp.setName(name);
        mp.setAddress(address);
        mp.setLat(45.7);
        mp.setLng(4.8);
        mp.setActive(true);
        return mp;
    }
}
