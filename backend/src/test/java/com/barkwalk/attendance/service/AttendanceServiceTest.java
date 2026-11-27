package com.barkwalk.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.barkwalk.attendance.domain.Attendance;
import com.barkwalk.attendance.dto.AttendanceDtos.PendingRollcallWalk;
import com.barkwalk.attendance.dto.AttendanceDtos.PointRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallItem;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallRequest;
import com.barkwalk.attendance.dto.AttendanceDtos.RollcallResponse;
import com.barkwalk.attendance.repository.AttendanceRepository;
import com.barkwalk.attendance.repository.WalkReportDogNoteRepository;
import com.barkwalk.attendance.repository.WalkReportRepository;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.service.CommunityOrgService;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WalkReportRepository walkReportRepository;
    @Mock private WalkReportDogNoteRepository noteRepository;
    @Mock private CommunityOrgService communityOrgService;
    @Mock private WalkRepository walkRepository;
    @Mock private WalkerProfileRepository walkerProfileRepository;
    @Mock private CityRepository cityRepository;
    @Mock private DogRepository dogRepository;
    @Mock private PrivacyService privacyService;

    @InjectMocks
    private AttendanceService attendanceService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    /** Retourne uniquement les balades terminées ayant au moins une réservation confirmée. */
    @Test
    void pendingRollcalls_filtersEndedWalksWithConfirmedBookings() {
        SecurityTestSupport.authenticate(1L, "admin@example.com", Role.USER, Role.ADMIN);

        Walk ended = walk(10L);
        ended.setCityId(3L);
        ended.setStartsAt(Instant.now().minus(Duration.ofHours(2)));
        ended.setDurationMinutes(60);

        Walk future = walk(20L);
        future.setCityId(3L);
        future.setStartsAt(Instant.now().plus(Duration.ofHours(1)));
        future.setDurationMinutes(60);

        when(walkerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(walkRepository.findPendingAttendanceCandidates()).thenReturn(List.of(ended, future));
        when(bookingRepository.countByWalkIdAndStatusIn(eq(10L), any())).thenReturn(2L);

        City city = new City();
        city.setName("Lyon");
        when(cityRepository.findById(3L)).thenReturn(Optional.of(city));

        List<PendingRollcallWalk> result = attendanceService.pendingRollcalls();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).walkId()).isEqualTo(10L);
        assertThat(result.get(0).confirmedCount()).isEqualTo(2);
        assertThat(result.get(0).cityName()).isEqualTo("Lyon");
    }

    /** Refuse le pointage si la balade n'est pas encore terminée. */
    @Test
    void submitRollcall_rejectsBeforeWalkEnd() {
        SecurityTestSupport.authenticate(1L, "org@example.com", Role.USER);

        Walk walk = walk(10L);
        walk.setOrganizerUserId(1L);
        walk.setStartsAt(Instant.now().plus(Duration.ofHours(2)));
        walk.setDurationMinutes(60);

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        RollcallRequest request = new RollcallRequest(List.of(new RollcallItem(1L, "PRESENT")));

        assertThatThrownBy(() -> attendanceService.submitRollcall(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Refuse un second pointage si la balade a déjà été validée. */
    @Test
    void submitRollcall_rejectsAlreadyValidated() {
        SecurityTestSupport.authenticate(1L, "org@example.com", Role.USER);

        Walk walk = walk(10L);
        walk.setOrganizerUserId(1L);
        walk.setStartsAt(Instant.now().minus(Duration.ofHours(2)));
        walk.setDurationMinutes(60);
        walk.setAttendanceValidatedAt(Instant.now().minus(Duration.ofMinutes(30)));

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        RollcallRequest request = new RollcallRequest(List.of(new RollcallItem(1L, "PRESENT")));

        assertThatThrownBy(() -> attendanceService.submitRollcall(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** Passe les PRESENT en COMPLETED et les ABSENT en NO_SHOW, puis valide la balade. */
    @Test
    void submitRollcall_marksPresentAsCompleted_absentAsNoShow() {
        SecurityTestSupport.authenticate(1L, "org@example.com", Role.USER);

        Walk walk = walk(10L);
        walk.setOrganizerUserId(1L);
        walk.setStartsAt(Instant.now().minus(Duration.ofHours(2)));
        walk.setDurationMinutes(60);

        Booking b1 = booking(1L, 10L, 2L);
        Booking b2 = booking(2L, 10L, 3L);

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(10L)).thenReturn(List.of(b1, b2));
        when(attendanceRepository.findByBookingId(1L)).thenReturn(Optional.empty());
        when(attendanceRepository.findByBookingId(2L)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        RollcallRequest request = new RollcallRequest(List.of(
                new RollcallItem(1L, "PRESENT"),
                new RollcallItem(2L, "ABSENT")));

        RollcallResponse response = attendanceService.submitRollcall(10L, request);

        assertThat(response.presentCount()).isEqualTo(1);
        assertThat(response.absentCount()).isEqualTo(1);
        assertThat(response.attendanceValidatedAt()).isNotNull();
        assertThat(b1.getStatus()).isEqualTo("COMPLETED");
        assertThat(b2.getStatus()).isEqualTo("NO_SHOW");
    }

    /** Interdit l'accès au pointage pour un utilisateur sans lien avec la balade. */
    @Test
    void requireStaff_forbidsRandomUser() {
        SecurityTestSupport.authenticate(1L, "stranger@example.com", Role.USER);

        Walk walk = walk(10L);
        walk.setOrganizerUserId(99L); // pas l'utilisateur courant
        // walkerId = null → vérification walker sautée

        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));

        assertThatThrownBy(() -> attendanceService.roster(10L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Crée une présence de départ pour une réservation confirmée. */
    @Test
    void departure_createsAttendance() {
        SecurityTestSupport.authenticate(1L, "org@example.com", Role.USER);

        Booking booking = booking(5L, 10L, 2L);

        Walk walk = walk(10L);
        walk.setOrganizerUserId(1L);

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(walkRepository.findById(10L)).thenReturn(Optional.of(walk));
        when(attendanceRepository.findByBookingId(5L)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = attendanceService.departure(new PointRequest(5L, "PRESENT"));

        assertThat(response.bookingId()).isEqualTo(5L);
        assertThat(response.departureStatus()).isEqualTo("PRESENT");
        assertThat(response.departureAt()).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Walk walk(long id) {
        Walk walk = new Walk();
        try {
            var f = Walk.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(walk, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        walk.setWalkType("STAFF_GUIDED");
        walk.setStatus("PUBLISHED");
        walk.setTitle("Balade test");
        return walk;
    }

    private static Booking booking(long id, long walkId, long userId) {
        Booking booking = new Booking();
        try {
            var f = Booking.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(booking, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        booking.setWalkId(walkId);
        booking.setUserId(userId);
        booking.setStatus("CONFIRMED");
        return booking;
    }
}
