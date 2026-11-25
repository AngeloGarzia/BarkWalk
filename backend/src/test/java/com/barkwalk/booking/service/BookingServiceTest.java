package com.barkwalk.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private DogRepository dogRepository;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(1L, "owner@example.com", Role.USER, Role.MEMBER);
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void create_confirmsFreeStaffWalkForMember() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(eq(10L), eq(5L), any())).thenReturn(false);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            try {
                var f = Booking.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(b, 100L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return b;
        });

        var response = bookingService.create(new BookingRequest(10L, 5L, false, false));
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.paymentRequired()).isFalse();
    }

    @Test
    void create_rejectsUnvalidatedDog() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        dog.setDossierStatus("DRAFT");
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_rejectsForeignDog() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 99L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancel_marksCancelledFree() {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setWalkId(10L);
        booking.setDogId(5L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));

        var response = bookingService.cancel(3L);
        assertThat(response.status()).isEqualTo("CANCELLED_FREE");
        assertThat(booking.getCancelledByRole()).isEqualTo("USER");
    }

    @Test
    void confirmPayment_setsConfirmed() {
        Booking booking = new Booking();
        booking.setStatus("PENDING_PAYMENT");
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));
        var response = bookingService.confirmPayment(8L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(booking.getConfirmedAt()).isNotNull();
    }

    @Test
    void mine_delegatesToRepository() {
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertThat(bookingService.mine()).isEmpty();
        verify(bookingRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void create_requiresCommunityAck() {
        Walk walk = staffWalk(10L, 8);
        walk.setWalkType("COMMUNITY");
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_pendingPaymentForIndep() {
        Walk walk = staffWalk(10L, 8);
        walk.setWalkType("INDEP_GUIDED");
        walk.setPriceCents(2000);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(eq(10L), eq(5L), any())).thenReturn(false);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            try {
                var f = Booking.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(b, 101L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return b;
        });
        var response = bookingService.create(new BookingRequest(10L, 5L, false, false));
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.paymentRequired()).isTrue();
        assertThat(response.priceCents()).isEqualTo(2000);
    }

    @Test
    void create_rejectsUnpublishedWalk() {
        Walk walk = staffWalk(10L, 8);
        walk.setStatus("DRAFT");
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_rejectsIncompatibleDog() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        dog.setSizeCategory("TINY");
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_rejectsDuplicateBooking() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(eq(10L), eq(5L), any())).thenReturn(true);
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_nonMemberPaysForStaffWalk() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(1L, "guest@example.com", Role.USER);
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(eq(10L), eq(5L), any())).thenReturn(false);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            try {
                var f = Booking.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(b, 102L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return b;
        });
        var response = bookingService.create(new BookingRequest(10L, 5L, false, false));
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.priceCents()).isEqualTo(1500);
    }

    @Test
    void create_communityRequiresMembership() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(1L, "guest@example.com", Role.USER);
        Walk walk = staffWalk(10L, 8);
        walk.setWalkType("COMMUNITY");
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, true, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancel_forbiddenForOtherUser() {
        Booking booking = new Booking();
        booking.setUserId(99L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> bookingService.cancel(3L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_fullWalkGoesToWaitlist() {
        Walk walk = staffWalk(10L, 1);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByWalkIdAndDogIdAndStatusNotIn(eq(10L), eq(5L), any())).thenReturn(false);
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(1L);
        when(waitlistEntryRepository.countByWalkId(10L)).thenReturn(0L);
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(waitlistEntryRepository).save(any());
    }

    private static Walk staffWalk(long id, int capacity) {
        Walk walk = new Walk();
        try {
            var f = Walk.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(walk, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        walk.setStatus("PUBLISHED");
        walk.setWalkType("STAFF_GUIDED");
        walk.setCapacity(capacity);
        walk.setPriceCents(0);
        walk.setNonMemberPriceCents(1500);
        walk.setAcceptedSizes(new String[] {"LARGE"});
        walk.setAcceptedEnergy(new String[] {"HIGH"});
        return walk;
    }

    private static Dog validatedDog(long id, long ownerId) {
        Dog dog = new Dog();
        try {
            var f = Dog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(dog, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        dog.setOwnerId(ownerId);
        dog.setDossierStatus("VALIDATED");
        dog.setSizeCategory("LARGE");
        dog.setEnergyLevel("HIGH");
        return dog;
    }
}
