package com.barkwalk.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barkwalk.booking.dto.BookingDtos.BookingRequest;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.common.Role;
import com.barkwalk.support.PostgresTestcontainers;
import com.barkwalk.support.SecurityTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Liste d'attente sur une vraie base : l'entrée doit survivre à la réponse 409 « balade complète ».
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BookingWaitlistIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingService bookingService;

    private long walkId;
    private long ownerId;
    private long ownerDogId;

    @BeforeEach
    void fullWalk() {
        long cityId = jdbc.queryForObject(
                """
                INSERT INTO cities (insee_code, postal_code, name, department, region, lat, lng)
                VALUES ('69123', ?, 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.76, 4.83)
                RETURNING id
                """,
                Long.class,
                String.valueOf(10000 + (int) (Math.random() * 89999)));
        long walkerUserId = insertUser();
        long walkerProfileId = jdbc.queryForObject(
                "INSERT INTO walker_profiles (user_id, walker_type, status) VALUES (?, 'STAFF', 'VALIDATED') RETURNING id",
                Long.class,
                walkerUserId);
        long meetingPointId = jdbc.queryForObject(
                "INSERT INTO meeting_points (city_id, name, address, lat, lng) VALUES (?, 'Parc', '1 rue', 45.78, 4.85) RETURNING id",
                Long.class,
                cityId);
        walkId = jdbc.queryForObject(
                """
                INSERT INTO walks (
                    city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes,
                    capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
                VALUES (?, ?, 'STAFF_GUIDED', 'Complète', ?, 60, 1, 0, ?, 'PUBLISHED', ?, ?)
                RETURNING id
                """,
                Long.class,
                cityId,
                meetingPointId,
                Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)),
                walkerProfileId,
                new String[] {"LARGE"},
                new String[] {"HIGH"});

        long otherUserId = insertUser();
        long otherDogId = insertValidatedDog(otherUserId);
        jdbc.update(
                "INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents) VALUES (?, ?, ?, 'CONFIRMED', 0)",
                walkId,
                otherUserId,
                otherDogId);

        ownerId = insertUser();
        ownerDogId = insertValidatedDog(ownerId);
        SecurityTestSupport.authenticate(ownerId, "owner@example.com", Role.USER, Role.MEMBER);
    }

    @AfterEach
    void clearAuth() {
        SecurityTestSupport.clear();
    }

    @Test
    void fullWalk_answersConflict_andKeepsTheWaitlistEntry() {
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(walkId, ownerDogId, false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(waitingEntries()).isEqualTo(1);
    }

    @Test
    void fullWalk_secondAttemptForSameDog_answersConflict_withoutDuplicateEntry() {
        BookingRequest request = new BookingRequest(walkId, ownerDogId, false, false);
        assertThatThrownBy(() -> bookingService.create(request)).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(waitingEntries()).isEqualTo(1);
    }

    private Integer waitingEntries() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM waitlist_entries WHERE walk_id = ? AND dog_id = ? AND status = 'WAITING'",
                Integer.class,
                walkId,
                ownerDogId);
    }

    private long insertUser() {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, email_verified, enabled, deleted)
                VALUES (?, 'hash', TRUE, TRUE, FALSE)
                RETURNING id
                """,
                Long.class,
                "u-" + UUID.randomUUID() + "@example.com");
    }

    private long insertValidatedDog(long ownerUserId) {
        return jdbc.queryForObject(
                """
                INSERT INTO dogs (
                    owner_id, name, breed, sex, size_category, energy_level,
                    sociability_dogs, sociability_humans, dossier_status)
                VALUES (?, 'Rex', 'Berger', 'MALE', 'LARGE', 'HIGH', 'GOOD', 'GOOD', 'VALIDATED')
                RETURNING id
                """,
                Long.class,
                ownerUserId);
    }
}
