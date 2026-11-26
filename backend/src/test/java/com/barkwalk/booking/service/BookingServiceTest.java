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
import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.NotificationService;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.service.DogWalkCompatibilityService;
import com.barkwalk.walker.repository.WalkerProfileRepository;
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
    @Mock
    private DogDossierService dogDossierService;
    @Mock
    private DogWalkCompatibilityService compatibilityService;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private WaitlistService waitlistService;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void auth() {
        SecurityTestSupport.authenticate(1L, "owner@example.com", Role.USER, Role.MEMBER);
        org.mockito.Mockito.lenient().doNothing().when(dogDossierService).assertBookable(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(compatibilityService)
                .assertCompatible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.lenient().when(businessSettingsService.pendingPaymentMinutes()).thenReturn(15);
        org.mockito.Mockito.lenient().when(businessSettingsService.cancellationFreeHours()).thenReturn(24);
        org.mockito.Mockito.lenient().doNothing().when(waitlistService).offerNext(org.mockito.ArgumentMatchers.anyLong());
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
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog dossier not validated"))
                .when(dogDossierService)
                .assertBookable(dog);

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_rejectsExpiredDog() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        dog.setDossierStatus("EXPIRED");
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dog dossier expired"))
                .when(dogDossierService)
                .assertBookable(dog);

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
    void cancel_marksCancelledFreeWhenBeforeDeadline() {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setWalkId(10L);
        booking.setDogId(5L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        Walk walk = staffWalk(10L, 8);
        walk.setStartsAt(java.time.Instant.now().plus(java.time.Duration.ofHours(30)));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        var response = bookingService.cancel(3L);
        assertThat(response.status()).isEqualTo("CANCELLED_FREE");
        assertThat(booking.getCancelledByRole()).isEqualTo("USER");
        assertThat(booking.isCountsTowardMembershipQuota()).isFalse();
    }

    @Test
    void cancel_marksCancelledLateWhenInside24h() {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setWalkId(10L);
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        Walk walk = staffWalk(10L, 8);
        walk.setStartsAt(java.time.Instant.now().plus(java.time.Duration.ofHours(10)));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        var response = bookingService.cancel(3L);
        assertThat(response.status()).isEqualTo("CANCELLED_LATE");
        assertThat(booking.isCountsTowardMembershipQuota()).isTrue();
        assertThat(booking.isCountsTowardOrgThreshold()).isTrue();
    }

    @Test
    void cancelByStaff_refundsAndNotifies() {
        SecurityTestSupport.clear();
        SecurityTestSupport.authenticate(2L, "admin@example.com", Role.USER, Role.ADMIN);
        Booking booking = new Booking();
        try {
            var f = Booking.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(booking, 3L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        booking.setUserId(1L);
        booking.setWalkId(10L);
        booking.setStatus("CONFIRMED");
        booking.setPaymentRequired(true);
        booking.setPriceCents(1500);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        Walk walk = staffWalk(10L, 8);
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(paymentRepository.findByBookingId(3L)).thenReturn(Optional.empty());

        var response = bookingService.cancelByStaff(3L, "weather");
        assertThat(response.status()).isEqualTo("CANCELLED_FREE");
        assertThat(booking.getCancelledByRole()).isEqualTo("ADMIN");
        verify(notificationService).notifyWalkCancelledByStaff(10L, 1L, "weather");
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
    void confirmPayment_isIdempotentWhenAlreadyConfirmed() {
        Booking booking = new Booking();
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(java.time.Instant.parse("2026-01-01T10:00:00Z"));
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));
        var response = bookingService.confirmPayment(8L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(booking.getConfirmedAt()).isEqualTo(java.time.Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void expireUnpaidBookings_releasesPlace() {
        Booking booking = new Booking();
        booking.setStatus("PENDING_PAYMENT");
        booking.setPaymentExpiresAt(java.time.Instant.now().minusSeconds(60));
        when(bookingRepository.findByStatusAndPaymentExpiresAtBefore(
                        org.mockito.ArgumentMatchers.eq("PENDING_PAYMENT"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        assertThat(bookingService.expireUnpaidBookings()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo("CANCELLED_FREE");
        assertThat(booking.getCancellationReason()).isEqualTo("payment_expired");
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
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size TINY not accepted for this walk"))
                .when(compatibilityService)
                .assertCompatible(walk, dog);
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Size");
                });
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
    void create_rejectsSecondTrial() {
        Walk walk = staffWalk(10L, 8);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(bookingRepository.existsByUserIdAndTrialTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("Free trial already used");
                });
    }

    @Test
    void create_rejectsTrialOnIndepWalk() {
        Walk walk = staffWalk(10L, 8);
        walk.setWalkType("INDEP_GUIDED");
        walk.setPriceCents(2000);
        Dog dog = validatedDog(5L, 1L);
        when(walkRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(walk));
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(10L, 5L, false, true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("staff-guided");
                });
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
