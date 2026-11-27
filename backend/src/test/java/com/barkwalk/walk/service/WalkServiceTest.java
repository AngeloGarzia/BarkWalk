package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.repository.WaitlistEntryRepository;
import com.barkwalk.review.domain.WalkRating;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkHistoryResponse;
import com.barkwalk.walk.dto.WalkPhotoDtos.WalkPhotoResponse;
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.review.repository.WalkRatingRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.dto.WalkDtos.WalkCreateRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkRecycleRequest;
import com.barkwalk.walk.dto.WalkDtos.WalkUpdateRequest;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import com.barkwalk.walker.service.WalkerPublishEligibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalkServiceTest {

    @Mock
    private WalkRepository walkRepository;
    @Mock
    private MeetingPointRepository meetingPointRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private PrivacyService privacyService;
    @Mock
    private DogRepository dogRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private WalkerPublishEligibility walkerPublishEligibility;
    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;
    @Mock
    private WalkRatingRepository walkRatingRepository;
    @Mock
    private WalkPhotoService walkPhotoService;
    @Mock
    private MessagingService messagingService;
    @Spy
    private WalkDifficultyCalculator difficultyCalculator = new WalkDifficultyCalculator();

    @InjectMocks
    private WalkService walkService;

    @BeforeEach
    void stubs() {
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(walkerPublishEligibility)
                .assertCanPublish(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.lenient()
                .when(privacyService.displayName(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("DemoUser");
        org.mockito.Mockito.lenient()
                .when(walkPhotoService.promoUrls(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(walkPhotoService.galleryCount(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(0L);
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Délègue au repository pour lister les balades du catalogue.
    @Test
    void catalogue_delegates() {
        when(walkRepository.catalogue(1L, null, Instant.EPOCH)).thenReturn(List.of());
        assertThat(walkService.catalogue(1L, null, Instant.EPOCH)).isEmpty();
        verify(walkRepository).catalogue(1L, null, Instant.EPOCH);
    }

    // Retourne 404 si la balade n'existe pas.
    @Test
    void get_notFound() {
        when(walkRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> walkService.get(9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // Retourne une balade publiée visible.
    @Test
    void get_returnsPublishedWalk() {
        Walk walk = publishedWalk(9L, 5L);
        when(walkRepository.findById(9L)).thenReturn(Optional.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.get(9L);
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    // Archive une balade et annule les réservations actives.
    @Test
    void archive_cancelsActiveBookings() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Walk walk = publishedWalk(40L, 5L);
        Booking booking = new Booking();
        setId(booking, 1L);
        booking.setWalkId(40L);
        booking.setUserId(8L);
        booking.setStatus("CONFIRMED");
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(40L)).thenReturn(List.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.archive(40L);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(walk.getArchivedAt()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo("CANCELLED_FREE");
        assertThat(booking.getCancellationReason()).isEqualTo("walk_archived");
    }

    // Construit l'historique photos + commentaires sur la lignée recycled_from.
    @Test
    void history_buildsLineageAndComments() {
        Walk current = publishedWalk(50L, 5L);
        current.setRecycledFromWalkId(40L);
        Walk parent = publishedWalk(40L, 5L);
        WalkRating rating = new WalkRating();
        setId(rating, 1L);
        rating.setWalkId(40L);
        rating.setUserId(8L);
        rating.setRating(5);
        rating.setComment("Super balade");
        when(walkRepository.findById(50L)).thenReturn(Optional.of(current));
        when(walkRepository.findById(40L)).thenReturn(Optional.of(parent));
        when(walkPhotoService.listForWalks(List.of(40L)))
                .thenReturn(List.of(new WalkPhotoResponse(
                        1L, 40L, "PROMO", "/p", (short) 0, null, 5L, "Org", Instant.now())));
        when(walkRatingRepository.findByWalkIdInOrderByCreatedAtDesc(List.of(40L))).thenReturn(List.of(rating));
        when(privacyService.displayName(8L)).thenReturn("Alice");

        WalkHistoryResponse history = walkService.history(50L);
        assertThat(history.lineageWalkIds()).containsExactly(40L);
        assertThat(history.photos()).hasSize(1);
        assertThat(history.ratingComments()).hasSize(1);
        assertThat(history.ratingComments().getFirst().displayName()).isEqualTo("Alice");
        assertThat(history.ratingComments().getFirst().comment()).isEqualTo("Super balade");
    }

    // Liste les balades de l'utilisateur (organisateur / walker).
    @Test
    void mine_returnsMappedWalks() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Walk walk = publishedWalk(40L, 5L);
        when(walkerProfileRepository.findByUserId(5L)).thenReturn(Optional.empty());
        when(walkRepository.findMine(5L, null)).thenReturn(List.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        assertThat(walkService.mine()).hasSize(1);
        assertThat(walkService.mine().getFirst().id()).isEqualTo(40L);
    }

    // Recycle une balade terminée vers une nouvelle date.
    @Test
    void recycle_createsPublishedClone() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER, Role.COMMUNITY_ORG);
        Walk source = publishedWalk(40L, 5L);
        source.setStatus("COMPLETED");
        source.setArchivedAt(Instant.now().minusSeconds(3600));
        source.setElevationGainM(10);
        source.setTerrainType("PARK");
        source.setDifficultyLevel(3);
        when(walkRepository.findById(40L)).thenReturn(Optional.of(source));
        when(profileRepository.findById(5L)).thenAnswer(inv -> {
            Profile p = new Profile();
            p.setCommunityOrgStatus("ACTIVE");
            p.setCompletedWalksCount(10);
            return Optional.of(p);
        });
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);
        when(businessSettingsService.communityCapacityMax()).thenReturn(8);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 99L);
            return w;
        });
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.recycle(40L, new WalkRecycleRequest(Instant.now().plusSeconds(172800)));
        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.recycledFromWalkId()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(messagingService).ensureWalkThread(any(Walk.class));
    }

    // Suppression admin d'une balade sans réservation.
    @Test
    void deleteByAdmin_removesWalkWithoutBookings() {
        SecurityTestSupport.authenticate(1L, "admin@barkwalk.fr", Role.USER, Role.ADMIN);
        Walk walk = publishedWalk(40L, 5L);
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(40L)).thenReturn(List.of());

        walkService.deleteByAdmin(40L);
        verify(waitlistEntryRepository).deleteByWalkId(40L);
        verify(walkRatingRepository).deleteByWalkId(40L);
        verify(walkRepository).delete(walk);
    }

    // Démarre une balade publiée.
    @Test
    void start_setsInProgress() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Walk walk = publishedWalk(40L, 5L);
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.start(40L);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(walk.getStartedAt()).isNotNull();
    }

    // Termine une balade en cours et l'archive.
    @Test
    void complete_setsCompletedAndArchived() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Walk walk = publishedWalk(40L, 5L);
        walk.setStatus("IN_PROGRESS");
        walk.setStartedAt(Instant.now().minusSeconds(1800));
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.complete(40L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(walk.getCompletedAt()).isNotNull();
        assertThat(walk.getArchivedAt()).isNotNull();
    }

    // Vue admin catalogue filtrée.
    @Test
    void adminOverview_listsWalksForAdmin() {
        SecurityTestSupport.authenticate(1L, "admin@barkwalk.fr", Role.USER, Role.ADMIN);
        Walk walk = publishedWalk(40L, 5L);
        when(walkRepository.findAdminOverview(null, "PUBLISHED")).thenReturn(List.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        assertThat(walkService.adminOverview(null, "PUBLISHED")).hasSize(1);
    }

    // Archives admin.
    @Test
    void archived_listsForAdmin() {
        SecurityTestSupport.authenticate(1L, "admin@barkwalk.fr", Role.USER, Role.ADMIN);
        Walk walk = publishedWalk(40L, 5L);
        walk.setArchivedAt(Instant.now());
        when(walkRepository.findArchived()).thenReturn(List.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        assertThat(walkService.archived()).hasSize(1);
        assertThat(walkService.archivedCatalogue(null)).hasSize(1);
    }

    // Agenda réservé admin / organisateur.
    @Test
    void calendarOverview_listsForOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.COMMUNITY_ORG);
        Walk walk = publishedWalk(40L, 5L);
        when(walkRepository.findAdminOverview(null, null)).thenReturn(List.of(walk));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        assertThat(walkService.calendarOverview()).hasSize(1);
    }

    // Filtre admin statut inconnu.
    @Test
    void adminOverview_rejectsUnknownStatus() {
        SecurityTestSupport.authenticate(1L, "admin@barkwalk.fr", Role.USER, Role.ADMIN);
        assertThatThrownBy(() -> walkService.adminOverview(null, "NOPE"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Liste les participants pour l'organisateur.
    @Test
    void participants_listsNamesForOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Walk walk = publishedWalk(40L, 5L);
        Booking booking = new Booking();
        setId(booking, 1L);
        booking.setWalkId(40L);
        booking.setUserId(8L);
        booking.setDogId(3L);
        booking.setStatus("CONFIRMED");
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));
        when(bookingRepository.findByWalkId(40L)).thenReturn(List.of(booking));
        when(dogRepository.findById(3L)).thenAnswer(inv -> {
            com.barkwalk.dog.domain.Dog dog = new com.barkwalk.dog.domain.Dog();
            dog.setName("Rex");
            return Optional.of(dog);
        });
        when(privacyService.displayName(8L)).thenReturn("Alice");
        when(privacyService.phoneForViewer(5L, 8L, 40L)).thenReturn(null);

        var response = walkService.participants(40L);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().getFirst().displayName()).isEqualTo("Alice");
        assertThat(response.participants().getFirst().dogName()).isEqualTo("Rex");
    }

    private static Walk publishedWalk(long id, long organizerId) {
        Walk walk = new Walk();
        setId(walk, id);
        walk.setOrganizerUserId(organizerId);
        walk.setStatus("PUBLISHED");
        walk.setWalkType("COMMUNITY");
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        walk.setTitle("Balade");
        walk.setStartsAt(Instant.now().plusSeconds(86400));
        walk.setDurationMinutes(60);
        walk.setCapacity(6);
        walk.setPriceCents(0);
        walk.setAcceptedSizes(new String[] {"LARGE"});
        walk.setAcceptedEnergy(new String[] {"HIGH"});
        return walk;
    }

    // Exige le rôle staff pour créer une balade staff.
    @Test
    void create_staff_requiresStaffRole() {
        SecurityTestSupport.authenticate(1L, "user@example.com", Role.USER);
        var req = createRequest("STAFF_GUIDED");
        assertThatThrownBy(() -> walkService.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // Crée une balade staff pour un marcheur accrédité.
    @Test
    void create_staff_okForWalker() {
        SecurityTestSupport.authenticate(2L, "staff@example.com", Role.USER, Role.WALKER_STAFF);
        when(meetingPointRepository.existsById(3L)).thenReturn(true);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 50L);
            return w;
        });
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));
        WalkerProfile walker = new WalkerProfile();
        setId(walker, 10L);
        walker.setUserId(2L);
        when(walkerProfileRepository.findById(10L)).thenReturn(Optional.of(walker));
        when(walkerProfileRepository.findByUserId(2L)).thenReturn(Optional.of(walker));

        var response = walkService.create(createRequest("STAFF_GUIDED"));
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.walkType()).isEqualTo("STAFF_GUIDED");
        assertThat(response.organizerUserId()).isEqualTo(2L);
    }

    // Interdit la création de balade communautaire si non éligible.
    @Test
    void create_community_forbiddenWhenNotEligible() {
        SecurityTestSupport.authenticate(4L, "member@example.com", Role.USER, Role.MEMBER);
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("NONE");
        profile.setCompletedWalksCount(0);
        when(profileRepository.findById(4L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);

        assertThatThrownBy(() -> walkService.create(createRequest("COMMUNITY")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // Crée une balade communautaire si organisateur actif.
    @Test
    void create_community_okWhenActiveOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("ACTIVE");
        profile.setCompletedWalksCount(10);
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);
        when(businessSettingsService.communityCapacityMax()).thenReturn(8);
        when(meetingPointRepository.existsById(3L)).thenReturn(true);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> {
            Walk w = inv.getArgument(0);
            setId(w, 77L);
            return w;
        });
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.create(createRequest("COMMUNITY"));
        assertThat(response.organizerUserId()).isEqualTo(5L);
        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.minSociabilityDogs()).isEqualTo("GOOD");
    }

    // Refuse une capacité supérieure au paramètre configuré.
    @Test
    void create_community_rejectsCapacityAboveSetting() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER);
        Profile profile = new Profile();
        profile.setCommunityOrgStatus("ACTIVE");
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(businessSettingsService.communityWalksThreshold()).thenReturn(3);
        when(businessSettingsService.communityCapacityMax()).thenReturn(8);
        when(meetingPointRepository.existsById(3L)).thenReturn(true);

        var req = new WalkCreateRequest(
                1L, 3L, "COMMUNITY", "Balade test", "desc",
                Instant.now().plusSeconds(86400), 60, 0, "FLAT", 12, 0, null, null,
                List.of("LARGE"), List.of("HIGH"), "GOOD", false, null, null, true);

        assertThatThrownBy(() -> walkService.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Community capacity exceeds max");
                });
    }

    private static WalkCreateRequest createRequest(String type) {
        return new WalkCreateRequest(
                1L,
                3L,
                type,
                "Balade test",
                "desc",
                Instant.now().plusSeconds(86400),
                60,
                0,
                "FLAT",
                6,
                0,
                1500,
                10L,
                List.of("LARGE"),
                List.of("HIGH"),
                "GOOD",
                false,
                null,
                null,
                true);
    }

    // Interdit la modification par un non-organisateur.
    @Test
    void update_forbiddenForNonOrganizer() {
        SecurityTestSupport.authenticate(9L, "other@example.com", Role.USER, Role.MEMBER);
        Walk walk = new Walk();
        setId(walk, 40L);
        walk.setOrganizerUserId(5L);
        walk.setStatus("PUBLISHED");
        walk.setWalkType("COMMUNITY");
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        when(walkRepository.findById(40L)).thenReturn(Optional.of(walk));

        var req = new WalkUpdateRequest(
                3L,
                "Nouveau titre",
                "desc",
                Instant.now().plusSeconds(86400),
                45,
                0,
                "FLAT",
                6,
                List.of("MEDIUM"),
                List.of("MEDIUM"),
                "AVERAGE",
                null,
                null);

        assertThatThrownBy(() -> walkService.update(40L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // Permet la modification par l'organisateur.
    @Test
    void update_okForOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER, Role.COMMUNITY_ORG);
        Walk walk = new Walk();
        setId(walk, 41L);
        walk.setOrganizerUserId(5L);
        walk.setStatus("PUBLISHED");
        walk.setWalkType("COMMUNITY");
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        walk.setCapacity(6);
        when(walkRepository.findById(41L)).thenReturn(Optional.of(walk));
        when(meetingPointRepository.existsById(3L)).thenReturn(true);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPointWithCity(1L)));
        when(businessSettingsService.communityCapacityMax()).thenReturn(8);
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var req = new WalkUpdateRequest(
                3L,
                "Titre modifié",
                "nouvelle desc",
                Instant.now().plusSeconds(90000),
                50,
                80,
                "PARK",
                5,
                List.of("SMALL", "MEDIUM"),
                List.of("LOW"),
                "GOOD",
                "Parc",
                null);

        var response = walkService.update(41L, req);
        assertThat(response.title()).isEqualTo("Titre modifié");
        assertThat(response.capacity()).isEqualTo(5);
        assertThat(response.durationMinutes()).isEqualTo(50);
        assertThat(response.elevationGainM()).isEqualTo(80);
        assertThat(response.terrainType()).isEqualTo("PARK");
        assertThat(response.difficultyLevel()).isBetween(1, 10);
    }

    // Démarre la balade par l'organisateur.
    @Test
    void start_okForOrganizer() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER, Role.COMMUNITY_ORG);
        Walk walk = new Walk();
        setId(walk, 50L);
        walk.setOrganizerUserId(5L);
        walk.setStatus("PUBLISHED");
        walk.setWalkType("COMMUNITY");
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        when(walkRepository.findById(50L)).thenReturn(Optional.of(walk));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.start(50L);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.startedAt()).isNotNull();
        assertThat(walk.getStatus()).isEqualTo("IN_PROGRESS");
    }

    // Termine une balade en cours.
    @Test
    void complete_fromInProgress() {
        SecurityTestSupport.authenticate(5L, "org@example.com", Role.USER, Role.MEMBER, Role.COMMUNITY_ORG);
        Walk walk = new Walk();
        setId(walk, 51L);
        walk.setOrganizerUserId(5L);
        walk.setStatus("IN_PROGRESS");
        walk.setStartedAt(Instant.now().minusSeconds(600));
        walk.setWalkType("COMMUNITY");
        walk.setCityId(1L);
        walk.setMeetingPointId(3L);
        when(walkRepository.findById(51L)).thenReturn(Optional.of(walk));
        when(walkRepository.save(any(Walk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.countByWalkIdAndStatusIn(any(), any())).thenReturn(0L);
        when(meetingPointRepository.findById(3L)).thenReturn(Optional.of(meetingPoint()));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

        var response = walkService.complete(51L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.archivedAt()).isNotNull();
    }

    // Refuse un itinéraire réduit à un seul point.
    @Test
    void normalizeRouteGeojson_rejectsSinglePoint() {
        Map<String, Object> node = Map.of(
                "type", "LineString",
                "coordinates", List.of(List.of(4.8, 45.7)));
        assertThatThrownBy(() -> walkService.normalizeRouteGeojson(node))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // Accepte un itinéraire valide avec au moins deux points.
    @Test
    void normalizeRouteGeojson_acceptsValidLine() {
        Map<String, Object> node = Map.of(
                "type", "LineString",
                "coordinates", List.of(List.of(4.8, 45.7), List.of(4.81, 45.71)));
        var out = walkService.normalizeRouteGeojson(node);
        assertThat(out.get("type")).isEqualTo("LineString");
        assertThat((List<?>) out.get("coordinates")).hasSize(2);
    }

    private static MeetingPoint meetingPoint() {
        return meetingPointWithCity(1L);
    }

    private static MeetingPoint meetingPointWithCity(Long cityId) {
        MeetingPoint mp = new MeetingPoint();
        setId(mp, 3L);
        mp.setCityId(cityId);
        mp.setName("Parc");
        mp.setAddress("1 rue");
        mp.setLat(45.7);
        mp.setLng(4.8);
        return mp;
    }

    private static City city() {
        City city = new City();
        setId(city, 1L);
        try {
            var name = City.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(city, "Lyon");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return city;
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
