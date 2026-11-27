package com.barkwalk.dog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.common.AuditService;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DogDossierServiceTest {

    @Mock
    private DogRepository dogRepository;
    @Mock
    private DogDocumentRepository documentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DogDossierService dogDossierService;

    // Autorise la réservation si dossier validé avec docs.
    @Test
    void assertBookable_allowsValidatedWithRequiredDocs() {
        Dog dog = dog(5L, "VALIDATED");
        when(documentRepository.findByDogId(5L)).thenReturn(List.of(
                validatedDoc("VACCINATION", LocalDate.now().plusMonths(6)),
                validatedDoc("INSURANCE", LocalDate.now().plusMonths(6))));

        dogDossierService.assertBookable(dog);
        assertThat(dog.getDossierStatus()).isEqualTo("VALIDATED");
    }

    // Refuse la réservation si dossier non validé.
    @Test
    void assertBookable_rejectsUnvalidated() {
        Dog dog = dog(5L, "DRAFT");
        assertThatThrownBy(() -> dogDossierService.assertBookable(dog))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("not validated");
                });
    }

    // Refuse la réservation si dossier expiré.
    @Test
    void assertBookable_rejectsExpiredDossier() {
        Dog dog = dog(5L, "EXPIRED");
        assertThatThrownBy(() -> dogDossierService.assertBookable(dog))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("expired");
                });
    }

    // Expire le dossier et signale les futures réservations.
    @Test
    void refreshExpiry_marksDossierExpiredAndFlagsFutureBooking() {
        Dog dog = dog(5L, "VALIDATED");
        DogDocument vaccine = validatedDoc("VACCINATION", LocalDate.now().minusDays(1));
        DogDocument insurance = validatedDoc("INSURANCE", LocalDate.now().plusMonths(6));
        when(documentRepository.findByDogId(5L)).thenReturn(List.of(vaccine, insurance));

        Booking booking = new Booking();
        booking.setWalkId(100L);
        booking.setDogId(5L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findByDogId(5L)).thenReturn(List.of(booking));

        Walk walk = new Walk();
        setId(walk, 100L);
        walk.setStartsAt(Instant.now().plus(3, ChronoUnit.DAYS));
        when(walkRepository.findById(100L)).thenReturn(Optional.of(walk));
        when(dogRepository.save(dog)).thenReturn(dog);
        when(bookingRepository.save(booking)).thenReturn(booking);

        dogDossierService.refreshExpiry(dog);

        assertThat(vaccine.getStatus()).isEqualTo("EXPIRED");
        assertThat(dog.getDossierStatus()).isEqualTo("EXPIRED");
        assertThat(booking.isAdminAlert()).isTrue();
        verify(documentRepository).save(vaccine);
        verify(auditService).log(eq(null), eq("DOG_DOSSIER_EXPIRED"), eq("DOG"), eq(5L), eq("scheduled"));
    }

    // Ne signale pas une réservation passée expirée.
    @Test
    void refreshExpiry_doesNotFlagPastWalk() {
        Dog dog = dog(5L, "VALIDATED");
        when(documentRepository.findByDogId(5L))
                .thenReturn(List.of(validatedDoc("VACCINATION", LocalDate.now().minusDays(1))));

        Booking booking = new Booking();
        booking.setWalkId(100L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findByDogId(5L)).thenReturn(List.of(booking));

        Walk walk = new Walk();
        setId(walk, 100L);
        walk.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(walkRepository.findById(100L)).thenReturn(Optional.of(walk));
        when(dogRepository.save(dog)).thenReturn(dog);

        dogDossierService.refreshExpiry(dog);

        assertThat(dog.getDossierStatus()).isEqualTo("EXPIRED");
        assertThat(booking.isAdminAlert()).isFalse();
        verify(bookingRepository, never()).save(any());
    }

    // Exige les deux types de documents obligatoires.
    @Test
    void hasValidRequiredDocuments_requiresBothTypes() {
        when(documentRepository.findByDogId(5L))
                .thenReturn(List.of(validatedDoc("VACCINATION", LocalDate.now().plusDays(10))));
        assertThat(dogDossierService.hasValidRequiredDocuments(5L)).isFalse();

        when(documentRepository.findByDogId(5L)).thenReturn(List.of(
                validatedDoc("VACCINATION", LocalDate.now().plusDays(10)),
                validatedDoc("INSURANCE", LocalDate.now().plusDays(10))));
        assertThat(dogDossierService.hasValidRequiredDocuments(5L)).isTrue();
    }

    private static Dog dog(long id, String status) {
        Dog dog = new Dog();
        setId(dog, id);
        dog.setDossierStatus(status);
        return dog;
    }

    private static DogDocument validatedDoc(String type, LocalDate expiresAt) {
        DogDocument doc = new DogDocument();
        doc.setDogId(5L);
        doc.setDocType(type);
        doc.setStorageKey("dogs/5/" + type.toLowerCase() + "-aaaaaaaaaaaaaaaa.pdf");
        doc.setExpiresAt(expiresAt);
        doc.setStatus("VALIDATED");
        return doc;
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
