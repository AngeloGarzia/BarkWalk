package com.barkwalk.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SchemaIntegrityStep1IT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("barkwalk").withUsername("barkwalk").withPassword("barkwalk");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("barkwalk.demo.seed-users", () -> "false");
        registry.add("barkwalk.jwt.secret", () -> "barkwalk-test-secret-key-32chars-min!!");
        registry.add("spring.docker.compose.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;

    private long cityId;
    private long userId;
    private long walkerUserId;
    private long walkerProfileId;
    private long meetingPointId;
    private long dogId;

    @BeforeEach
    void seedMinimalGraph() {
        Long existingCity = jdbc.query(
                "SELECT id FROM cities WHERE postal_code = '69001' LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (existingCity != null) {
            cityId = existingCity;
        } else {
            cityId = jdbc.queryForObject(
                    """
                    INSERT INTO cities (insee_code, postal_code, name, department, region, lat, lng)
                    VALUES ('69123', '69001', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.76, 4.83)
                    RETURNING id
                    """,
                    Long.class);
        }

        userId = insertUser("owner-" + UUID.randomUUID() + "@example.com");
        walkerUserId = insertUser("walker-" + UUID.randomUUID() + "@example.com");

        jdbc.update(
                """
                INSERT INTO profiles (user_id, first_name, last_name, city_id)
                VALUES (?, 'Ada', 'Lovelace', ?)
                """,
                userId,
                cityId);
        jdbc.update(
                """
                INSERT INTO profiles (user_id, first_name, last_name, city_id)
                VALUES (?, 'Sam', 'Walker', ?)
                """,
                walkerUserId,
                cityId);

        walkerProfileId = jdbc.queryForObject(
                """
                INSERT INTO walker_profiles (user_id, walker_type, status)
                VALUES (?, 'STAFF', 'VALIDATED')
                RETURNING id
                """,
                Long.class,
                walkerUserId);

        meetingPointId = jdbc.queryForObject(
                """
                INSERT INTO meeting_points (city_id, name, address, lat, lng)
                VALUES (?, 'Parc test', '1 rue test', 45.78, 4.85)
                RETURNING id
                """,
                Long.class,
                cityId);

        dogId = jdbc.queryForObject(
                """
                INSERT INTO dogs (
                    owner_id, name, breed, sex, size_category, energy_level,
                    sociability_dogs, sociability_humans, dossier_status)
                VALUES (?, 'Rex', 'Berger', 'MALE', 'LARGE', 'HIGH', 'GOOD', 'GOOD', 'VALIDATED')
                RETURNING id
                """,
                Long.class,
                userId);
    }

    @Test
    void staffWalk_withWalkerAndTypedArrays_isAccepted() {
        Long walkId = insertStaffWalk(8, new String[] {"SMALL", "MEDIUM"}, new String[] {"LOW", "MEDIUM"});
        assertThat(walkId).isPositive();

        String[] sizes = jdbc.queryForObject(
                "SELECT accepted_sizes FROM walks WHERE id = ?", String[].class, walkId);
        assertThat(sizes).containsExactly("SMALL", "MEDIUM");
    }

    @Test
    void communityWalk_withoutOrganizer_isRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO walks (
                            city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes,
                            capacity, price_cents, status, accepted_sizes, accepted_energy)
                        VALUES (?, ?, 'COMMUNITY', 'Bad community', ?, 45, 4, 0, 'PUBLISHED',
                                ARRAY['SMALL']::text[], ARRAY['LOW']::text[])
                        """,
                        cityId,
                        meetingPointId,
                        Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void communityWalk_capacityAboveEight_isRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO walks (
                            city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes,
                            capacity, price_cents, organizer_user_id, status, accepted_sizes, accepted_energy)
                        VALUES (?, ?, 'COMMUNITY', 'Too big', ?, 45, 9, 0, ?, 'PUBLISHED',
                                ARRAY['SMALL']::text[], ARRAY['LOW']::text[])
                        """,
                        cityId,
                        meetingPointId,
                        Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS)),
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staffWalk_withoutWalker_isRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO walks (
                            city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes,
                            capacity, price_cents, status, accepted_sizes, accepted_energy)
                        VALUES (?, ?, 'STAFF_GUIDED', 'No walker', ?, 60, 6, 0, 'PUBLISHED',
                                ARRAY['SMALL']::text[], ARRAY['LOW']::text[])
                        """,
                        cityId,
                        meetingPointId,
                        Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidAcceptedSize_isRejected() {
        assertThatThrownBy(() -> insertStaffWalk(6, new String[] {"TINY"}, new String[] {"LOW"}))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void booking_duplicateActiveSameDog_isRejected_butCancelledAllowsNew() {
        Long walkId = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});

        insertBooking(walkId, userId, dogId, "CONFIRMED", false);
        assertThatThrownBy(() -> insertBooking(walkId, userId, dogId, "PENDING_PAYMENT", false))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE walk_id = ? AND dog_id = ?", walkId, dogId);
        Long secondId = insertBooking(walkId, userId, dogId, "CONFIRMED", false);
        assertThat(secondId).isPositive();
    }

    @Test
    void trialBooking_secondTrialSameUser_isRejected() {
        Long walk1 = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});
        Long walk2 = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});

        insertBooking(walk1, userId, dogId, "CONFIRMED", true);
        assertThatThrownBy(() -> insertBooking(walk2, userId, dogId, "CONFIRMED", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void review_selfRating_isRejected() {
        Long walkId = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO reviews (walk_id, author_id, target_user_id, rating, comment)
                        VALUES (?, ?, ?, 5, 'self')
                        """,
                        walkId,
                        userId,
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void review_validRating_isAccepted() {
        Long walkId = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});
        Integer rating = jdbc.queryForObject(
                """
                INSERT INTO reviews (walk_id, author_id, target_user_id, rating, comment)
                VALUES (?, ?, ?, 4, 'nice')
                RETURNING rating
                """,
                Integer.class,
                walkId,
                userId,
                walkerUserId);
        assertThat(rating).isEqualTo(4);
    }

    @Test
    void userBlock_self_isRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?)", userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payment_negativeAmount_isRejected() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO payments (user_id, amount_cents, payment_type, status)
                        VALUES (?, -1, 'BOOKING', 'PENDING')
                        """,
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void booking_hasUpdatedAt_andNegativePriceRejected() {
        Long walkId = insertStaffWalk(6, new String[] {"LARGE"}, new String[] {"HIGH"});
        Long bookingId = insertBooking(walkId, userId, dogId, "CONFIRMED", false);
        Timestamp updatedAt = jdbc.queryForObject("SELECT updated_at FROM bookings WHERE id = ?", Timestamp.class, bookingId);
        assertThat(updatedAt).isNotNull();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents)
                        VALUES (?, ?, ?, 'CONFIRMED', -10)
                        """,
                        walkId,
                        userId,
                        dogId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String email) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, email_verified, enabled, deleted)
                VALUES (?, 'hash', TRUE, TRUE, FALSE)
                RETURNING id
                """,
                Long.class,
                email);
    }

    private Long insertStaffWalk(int capacity, String[] sizes, String[] energy) {
        return jdbc.queryForObject(
                """
                INSERT INTO walks (
                    city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes,
                    capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
                VALUES (?, ?, 'STAFF_GUIDED', ?, ?, 60, ?, 0, ?, 'PUBLISHED', ?, ?)
                RETURNING id
                """,
                Long.class,
                cityId,
                meetingPointId,
                "Walk " + UUID.randomUUID(),
                Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)),
                capacity,
                walkerProfileId,
                sizes,
                energy);
    }

    private Long insertBooking(long walkId, long uid, long did, String status, boolean trial) {
        return jdbc.queryForObject(
                """
                INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents, is_trial)
                VALUES (?, ?, ?, ?, 0, ?)
                RETURNING id
                """,
                Long.class,
                walkId,
                uid,
                did,
                status,
                trial);
    }
}
