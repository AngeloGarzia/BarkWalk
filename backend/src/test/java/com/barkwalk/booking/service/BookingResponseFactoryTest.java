package com.barkwalk.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.domain.WaitlistEntry;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.dto.BookingDtos.WaitlistEntryResponse;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Instant;
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

    // Mappe une entrée waitlist avec titre de balade et nom du chien.
    @Test
    void from_waitlistEntry_mapsFields() {
        WaitlistEntry entry = new WaitlistEntry();
        entry.setWalkId(10L);
        entry.setDogId(5L);
        entry.setPosition(2);
        entry.setStatus("WAITING");
        entry.setOfferedAt(null);
        entry.setOfferExpiresAt(null);
        try {
            var f = WaitlistEntry.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entry, 44L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Walk walk = new Walk();
        walk.setTitle("Balade lac");
        walk.setWalkType("STAFF_GUIDED");
        walk.setStartsAt(Instant.parse("2030-01-01T10:00:00Z"));
        walk.setOrganizerUserId(9L);

        Dog dog = new Dog();
        dog.setName("Nala");

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(privacyService.displayName(9L)).thenReturn("Org");

        WaitlistEntryResponse response = factory.from(entry);
        assertThat(response.id()).isEqualTo(44L);
        assertThat(response.walkTitle()).isEqualTo("Balade lac");
        assertThat(response.dogName()).isEqualTo("Nala");
        assertThat(response.organizerDisplayName()).isEqualTo("Org");
        assertThat(response.position()).isEqualTo(2);
    }

    // Résout le nom via le profil walker si pas d'organizerUserId.
    @Test
    void from_booking_resolvesWalkerOrganizerDisplayName() {
        Booking booking = new Booking();
        booking.setWalkId(10L);
        booking.setDogId(5L);
        booking.setUserId(1L);
        booking.setStatus("CONFIRMED");

        Walk walk = new Walk();
        walk.setTitle("Guidée");
        walk.setWalkType("STAFF_GUIDED");
        walk.setCityId(3L);
        walk.setWalkerId(77L);

        WalkerProfile walker = new WalkerProfile();
        try {
            var f = WalkerProfile.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(walker, 77L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        walker.setUserId(8L);

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.empty());
        when(cityRepository.findById(3L)).thenReturn(Optional.empty());
        when(walkerProfileRepository.findById(77L)).thenReturn(Optional.of(walker));
        when(privacyService.displayName(8L)).thenReturn("Walker");
        when(privacyService.displayName(1L)).thenReturn("Client");

        BookingResponse response = factory.from(booking);
        assertThat(response.organizerDisplayName()).isEqualTo("Walker");
    }
}
