package com.barkwalk.config;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "barkwalk.demo.seed-users", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private record DemoSpot(
            String postalCode,
            String meetingName,
            String address,
            double lat,
            double lng,
            String title,
            String description,
            String walkType,
            String routeHint,
            int daysFromNow,
            int durationMinutes,
            int capacity,
            int nonMemberPriceCents) {}

    private static final List<DemoSpot> FRANCE_SPOTS = List.of(
            new DemoSpot(
                    "69001",
                    "Parc de la Tête d'Or",
                    "Boulevard des Belges, 69006 Lyon",
                    45.781,
                    4.853,
                    "Balade matinale Tête d'Or",
                    "Balade encadrée pour chiens sociables autour du lac.",
                    "STAFF_GUIDED",
                    "Tour du lac ~4 km",
                    2,
                    60,
                    8,
                    1500),
            new DemoSpot(
                    "69001",
                    "Place Bellecour",
                    "Place Bellecour, 69002 Lyon",
                    45.7578,
                    4.8320,
                    "Balade communautaire Bellecour",
                    "Balade entre adhérents — non encadrée.",
                    "COMMUNITY",
                    "Berges du Rhône",
                    3,
                    45,
                    6,
                    0),
            new DemoSpot(
                    "75001",
                    "Jardin des Tuileries",
                    "Place de la Concorde, 75001 Paris",
                    48.8634,
                    2.3275,
                    "Balade Tuileries",
                    "Promenade encadrée au cœur de Paris.",
                    "STAFF_GUIDED",
                    "Allées du jardin ~3 km",
                    2,
                    55,
                    8,
                    1800),
            new DemoSpot(
                    "75011",
                    "Parc des Buttes-Chaumont",
                    "1 Rue Botzaris, 75019 Paris",
                    48.8800,
                    2.3822,
                    "Buttes-Chaumont canin",
                    "Relief et pelouses pour chiens énergiques.",
                    "STAFF_GUIDED",
                    "Boucle du parc ~3,5 km",
                    4,
                    60,
                    7,
                    1800),
            new DemoSpot(
                    "13001",
                    "Parc Borély",
                    "Avenue du Prado, 13008 Marseille",
                    43.2615,
                    5.3810,
                    "Balade Borély",
                    "Balade méditerranéenne entre parc et mer.",
                    "STAFF_GUIDED",
                    "Parc + corniche ~4 km",
                    3,
                    60,
                    8,
                    1600),
            new DemoSpot(
                    "31000",
                    "Jardin des Plantes",
                    "Allée Frédéric Mistral, 31000 Toulouse",
                    43.5935,
                    1.4520,
                    "Toulouse Jardin des Plantes",
                    "Balade urbaine ombragée pour tous gabarits.",
                    "STAFF_GUIDED",
                    "Jardin + Canal ~3 km",
                    5,
                    50,
                    8,
                    1400),
            new DemoSpot(
                    "33000",
                    "Jardin Public",
                    "Cours de Verdun, 33000 Bordeaux",
                    44.8480,
                    -0.5775,
                    "Balade Jardin Public Bordeaux",
                    "Promenade encadrée sous les platanes.",
                    "STAFF_GUIDED",
                    "Tour du jardin ~2,5 km",
                    4,
                    45,
                    6,
                    1500),
            new DemoSpot(
                    "44000",
                    "Île de Versailles",
                    "Quai de Versailles, 44000 Nantes",
                    47.2215,
                    -1.5540,
                    "Nantes Île de Versailles",
                    "Balade jardin japonais et bords de l'Erdre.",
                    "STAFF_GUIDED",
                    "Île + quais ~3 km",
                    6,
                    55,
                    7,
                    1400),
            new DemoSpot(
                    "67000",
                    "Parc de l'Orangerie",
                    "Avenue de l'Europe, 67000 Strasbourg",
                    48.5950,
                    7.7770,
                    "Orangerie Strasbourg",
                    "Balade familiale au parc de l'Orangerie.",
                    "STAFF_GUIDED",
                    "Boucle Orangerie ~3 km",
                    5,
                    50,
                    8,
                    1500),
            new DemoSpot(
                    "59000",
                    "Citadelle de Lille",
                    "Avenue du 43e RI, 59000 Lille",
                    50.6405,
                    3.0455,
                    "Balade Citadelle Lille",
                    "Grand espace vert pour chiens dynamiques.",
                    "STAFF_GUIDED",
                    "Tour de la citadelle ~4 km",
                    3,
                    60,
                    8,
                    1400),
            new DemoSpot(
                    "34000",
                    "Place de la Comédie",
                    "Place de la Comédie, 34000 Montpellier",
                    43.6085,
                    3.8795,
                    "Montpellier centre",
                    "Balade urbaine douce en centre-ville.",
                    "COMMUNITY",
                    "Écusson ~2 km",
                    7,
                    40,
                    5,
                    0),
            new DemoSpot(
                    "38000",
                    "Parc Paul Mistral",
                    "Boulevard Jean Pain, 38000 Grenoble",
                    45.1845,
                    5.7415,
                    "Grenoble Paul Mistral",
                    "Balade au pied des Alpes.",
                    "STAFF_GUIDED",
                    "Parc + berges ~3,5 km",
                    4,
                    55,
                    7,
                    1500));

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CityRepository cityRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final DogRepository dogRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final WalkRepository walkRepository;

    public DemoDataInitializer(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            PasswordEncoder passwordEncoder,
            CityRepository cityRepository,
            MembershipPlanRepository planRepository,
            MembershipRepository membershipRepository,
            DogRepository dogRepository,
            WalkerProfileRepository walkerProfileRepository,
            MeetingPointRepository meetingPointRepository,
            WalkRepository walkRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.cityRepository = cityRepository;
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.dogRepository = dogRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.walkRepository = walkRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long lyonId = cityRepository.search("69001", PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(City::getId)
                .orElseGet(() -> cityRepository.findAll().stream().findFirst().map(City::getId).orElse(null));

        User admin = upsertUser("admin@barkwalk.fr", Set.of(Role.ADMIN, Role.USER), "Admin", "BarkWalk", lyonId);
        User alice = upsertUser(
                "alice@example.com", Set.of(Role.USER, Role.MEMBER, Role.COMMUNITY_ORG), "Alice", "Martin", lyonId);
        User walker =
                upsertUser("walker@barkwalk.fr", Set.of(Role.USER, Role.WALKER_STAFF), "Sam", "Walker", lyonId);

        Profile aliceProfile = profileRepository.findById(alice.getId()).orElseThrow();
        aliceProfile.setCompletedWalksCount(3);
        aliceProfile.setCommunityOrgStatus("ACTIVE");
        aliceProfile.setPhone("+33600000001");

        if (dogRepository.findByOwnerIdOrderByNameAsc(alice.getId()).isEmpty()) {
            Dog dog = new Dog();
            dog.setOwnerId(alice.getId());
            dog.setName("Rex");
            dog.setBreed("Berger allemand");
            dog.setBirthDate(LocalDate.of(2020, 5, 12));
            dog.setSex("MALE");
            dog.setSterilized(true);
            dog.setWeightKg(new BigDecimal("32.50"));
            dog.setSizeCategory("LARGE");
            dog.setEnergyLevel("HIGH");
            dog.setSociabilityDogs("GOOD");
            dog.setSociabilityHumans("GOOD");
            dog.setDossierStatus("VALIDATED");
            dogRepository.save(dog);
        }

        MembershipPlan monthly = planRepository.findByActiveTrue().stream()
                .filter(p -> "MONTHLY".equals(p.getCode()))
                .findFirst()
                .orElse(null);
        if (monthly != null
                && membershipRepository
                        .findFirstByUserIdAndStatusOrderByCreatedAtDesc(alice.getId(), "ACTIVE")
                        .isEmpty()) {
            Membership membership = new Membership();
            membership.setUserId(alice.getId());
            membership.setPlanId(monthly.getId());
            membership.setStatus("ACTIVE");
            membership.setCurrentPeriodStart(Instant.now().minus(5, ChronoUnit.DAYS));
            membership.setCurrentPeriodEnd(Instant.now().plus(25, ChronoUnit.DAYS));
            membershipRepository.save(membership);
        }

        WalkerProfile walkerProfile = walkerProfileRepository.findByUserId(walker.getId()).orElseGet(() -> {
            WalkerProfile wp = new WalkerProfile();
            wp.setUserId(walker.getId());
            wp.setWalkerType("STAFF");
            wp.setStatus("VALIDATED");
            wp.setBio("Encadrant salarié Bark & Walk");
            wp.setAverageRating(new BigDecimal("4.80"));
            wp.setRatingsCount(12);
            wp.setCharterSignedAt(Instant.now());
            return walkerProfileRepository.save(wp);
        });

        seedFranceWalks(walkerProfile, alice);
    }

    private void seedFranceWalks(WalkerProfile walkerProfile, User alice) {
        for (DemoSpot spot : FRANCE_SPOTS) {
            City city = cityRepository.search(spot.postalCode(), PageRequest.of(0, 1)).stream()
                    .findFirst()
                    .orElse(null);
            if (city == null) {
                continue;
            }

            boolean alreadyHasPoint = meetingPointRepository.findByCityIdAndActiveTrue(city.getId()).stream()
                    .anyMatch(mp -> mp.getName().equalsIgnoreCase(spot.meetingName()));
            if (alreadyHasPoint) {
                continue;
            }

            MeetingPoint mp = new MeetingPoint();
            mp.setCityId(city.getId());
            mp.setName(spot.meetingName());
            mp.setAddress(spot.address());
            mp.setLat(spot.lat());
            mp.setLng(spot.lng());
            mp = meetingPointRepository.save(mp);

            Walk walk = new Walk();
            walk.setCityId(city.getId());
            walk.setMeetingPointId(mp.getId());
            walk.setWalkType(spot.walkType());
            walk.setTitle(spot.title());
            walk.setDescription(spot.description());
            walk.setStartsAt(Instant.now().plus(spot.daysFromNow(), ChronoUnit.DAYS));
            walk.setDurationMinutes(spot.durationMinutes());
            walk.setCapacity(spot.capacity());
            walk.setPriceCents(0);
            if (spot.nonMemberPriceCents() > 0) {
                walk.setNonMemberPriceCents(spot.nonMemberPriceCents());
            }
            if ("COMMUNITY".equals(spot.walkType())) {
                walk.setOrganizerUserId(alice.getId());
                walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM"});
                walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM"});
            } else {
                walk.setWalkerId(walkerProfile.getId());
                walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM", "LARGE"});
                walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM", "HIGH"});
                walk.setMinSociabilityDogs("GOOD");
            }
            walk.setStatus("PUBLISHED");
            walk.setRouteHint(spot.routeHint());
            walkRepository.save(walk);
        }
    }

    private User upsertUser(String email, Set<Role> roles, String firstName, String lastName, Long cityId) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Admin123!"));
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setDeleted(false);
        user.setRoles(new HashSet<>(roles));
        user.setUpdatedAt(Instant.now());
        user = userRepository.save(user);

        Profile profile = profileRepository.findById(user.getId()).orElseGet(Profile::new);
        profile.setUserId(user.getId());
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        if (profile.getCityId() == null) {
            profile.setCityId(cityId);
        }
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
        return user;
    }
}
