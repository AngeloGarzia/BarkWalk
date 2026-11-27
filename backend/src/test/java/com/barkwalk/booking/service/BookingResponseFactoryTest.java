package com.barkwalk.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingResponseFactoryTest {

    @Mock private WalkRepository walkRepository;
    @Mock private DogRepository dogRepository;
    @Mock private CityRepository cityRepository;
    @Mock private PrivacyService privacyService;
    @Mock private WalkerProfileRepository walkerProfileRepository;

    @InjectMocks
    private BookingResponseFactory factory;

    /**
     * Remplit le titre de la balade, la ville, le nom du chien et
     * le nom d'affichage de l'organisateur via les mocks.
     */
    @Test
    void from_fillsWalkTitleCityDogOrganizerDisplayNames() {
        Booking booking = new Booking();
        booking.setWalkId(10L);
        booking.setDogId(5L);
        booking.setUserId(1L);
        booking.setStatus("CONFIRMED");

        Walk walk = new Walk();
        walk.setTitle("Balade forêt de Sénart");
        walk.setWalkType("STAFF_GUIDED");
        walk.setCityId(3L);
        walk.setOrganizerUserId(2L);

        Dog dog = new Dog();
        dog.setName("Rex");
        dog.setOwnerId(1L);
        dog.setBreed("Labrador");
        dog.setSex("M");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");

        City city = new City();
        city.setName("Combs-la-Ville");
        city.setInseeCode("77122");
        city.setPostalCode("77380");
        city.setDepartment("77");
        city.setRegion("Île-de-France");

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(cityRepository.findById(3L)).thenReturn(Optional.of(city));
        when(privacyService.displayName(2L)).thenReturn("Marie Dupont");
        when(privacyService.displayName(1L)).thenReturn("Jean Martin");

        BookingResponse response = factory.from(booking);

        assertThat(response.walkTitle()).isEqualTo("Balade forêt de Sénart");
        assertThat(response.cityName()).isEqualTo("Combs-la-Ville");
        assertThat(response.dogName()).isEqualTo("Rex");
        assertThat(response.organizerDisplayName()).isEqualTo("Marie Dupont");
        assertThat(response.participantDisplayName()).isEqualTo("Jean Martin");
        assertThat(response.walkType()).isEqualTo("STAFF_GUIDED");
    }

    /** Renvoie null pour les champs enrichis si la balade est introuvable. */
    @Test
    void from_handlesAbsentWalk() {
        Booking booking = new Booking();
        booking.setWalkId(99L);
        booking.setDogId(5L);
        booking.setUserId(1L);
        booking.setStatus("CANCELLED_FREE");

        Dog dog = new Dog();
        dog.setName("Bella");
        dog.setOwnerId(1L);
        dog.setBreed("Beagle");
        dog.setSex("F");
        dog.setSizeCategory("MEDIUM");
        dog.setEnergyLevel("MEDIUM");
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");

        when(walkRepository.findById(99L)).thenReturn(Optional.empty());
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(privacyService.displayName(1L)).thenReturn("Jean Martin");

        BookingResponse response = factory.from(booking);

        assertThat(response.walkTitle()).isNull();
        assertThat(response.cityName()).isNull();
        assertThat(response.organizerDisplayName()).isNull();
        assertThat(response.dogName()).isEqualTo("Bella");
    }
}
