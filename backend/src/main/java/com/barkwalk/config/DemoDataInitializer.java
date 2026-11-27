package com.barkwalk.config;

import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import com.barkwalk.common.Role;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import com.barkwalk.dog.service.DogDossierService;
import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.domain.MembershipPlan;
import com.barkwalk.membership.repository.MembershipPlanRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.messaging.service.MessagingService;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.MeetingPoint;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.MeetingPointRepository;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walk.service.WalkDifficultyCalculator;
import com.barkwalk.walker.domain.WalkerProfile;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Jeu de données de démonstration chargé au démarrage si barkwalk.demo.seed-users est actif.
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
            int elevationGainM,
            String terrainType,
            int capacity,
            int priceCents,
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
                    40,
                    "PARK",
                    8,
                    1200,
                    1800),
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
                    15,
                    "FLAT",
                    6,
                    0,
                    0),
            new DemoSpot(
                    "69001",
                    "Croix-Rousse",
                    "Place de la Croix-Rousse, 69004 Lyon",
                    45.7745,
                    4.8322,
                    "Descente Croix-Rousse (passée)",
                    "Balade terminée — ouverte aux notes participants.",
                    "COMMUNITY",
                    "Pentes de la Croix-Rousse ~2,5 km",
                    -3,
                    50,
                    90,
                    "TRAIL",
                    6,
                    0,
                    0),
            new DemoSpot(
                    "69001",
                    "Parc de la Tête d'Or",
                    "Boulevard des Belges, 69006 Lyon",
                    45.781,
                    4.853,
                    "Tête d'Or coucher de soleil (archivée)",
                    "Balade encadrée du soir — terminée et archivée pour tests catalogue.",
                    "STAFF_GUIDED",
                    "Tour du lac au crépuscule ~3,5 km",
                    -8,
                    55,
                    35,
                    "PARK",
                    8,
                    1000,
                    1600),
            new DemoSpot(
                    "69001",
                    "Parc de Gerland",
                    "Avenue Tony Garnier, 69007 Lyon",
                    45.7265,
                    4.8255,
                    "Gerland canin (archivée)",
                    "Grande pelouse — édition passée archivée.",
                    "STAFF_GUIDED",
                    "Boucle Gerland ~4 km",
                    -14,
                    65,
                    50,
                    "PARK",
                    8,
                    1100,
                    1700),
            new DemoSpot(
                    "69001",
                    "Quai Saint-Vincent",
                    "Quai Saint-Vincent, 69001 Lyon",
                    45.7688,
                    4.8195,
                    "Berges Saône indépendant (archivée)",
                    "Balade indépendante passée — utile pour recyclage / historique.",
                    "INDEP_GUIDED",
                    "Berges amont ~3 km",
                    -21,
                    50,
                    40,
                    "FLAT",
                    6,
                    2200,
                    2200),
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
                    25,
                    "PARK",
                    8,
                    1400,
                    2000),
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
                    70,
                    160,
                    "TRAIL",
                    7,
                    1500,
                    2200),
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
                    55,
                    "PARK",
                    8,
                    1300,
                    1900),
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
                    30,
                    "PARK",
                    8,
                    1100,
                    1600),
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
                    20,
                    "PARK",
                    6,
                    1200,
                    1700),
            new DemoSpot(
                    "44000",
                    "Île de Versailles",
                    "Quai de Versailles, 44000 Nantes",
                    47.2215,
                    -1.5540,
                    "Nantes Île de Versailles",
                    "Balade jardin japonais et bords de l'Erdre.",
                    "INDEP_GUIDED",
                    "Île + quais ~3 km",
                    6,
                    55,
                    35,
                    "PARK",
                    7,
                    2500,
                    2500),
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
                    25,
                    "PARK",
                    8,
                    1200,
                    1700),
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
                    70,
                    "FOREST",
                    8,
                    1100,
                    1600),
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
                    10,
                    "FLAT",
                    5,
                    0,
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
                    75,
                    220,
                    "MOUNTAIN",
                    7,
                    1400,
                    2000),
            new DemoSpot(
                    "06000",
                    "Promenade des Anglais",
                    "Promenade des Anglais, 06000 Nice",
                    43.6950,
                    7.2650,
                    "Nice Promenade",
                    "Balade en bord de mer pour chiens sociables.",
                    "STAFF_GUIDED",
                    "Promenade ~3 km",
                    3,
                    50,
                    15,
                    "FLAT",
                    8,
                    1500,
                    2100),
            new DemoSpot(
                    "35000",
                    "Parc du Thabor",
                    "Place Saint-Mélaine, 35000 Rennes",
                    48.1145,
                    -1.6700,
                    "Rennes Thabor",
                    "Jardins et pelouses pour tous gabarits.",
                    "COMMUNITY",
                    "Tour du Thabor ~2,5 km",
                    5,
                    45,
                    20,
                    "PARK",
                    6,
                    0,
                    0),
            new DemoSpot(
                    "69001",
                    "Parc de la Tête d'Or",
                    "Boulevard des Belges, 69006 Lyon",
                    45.781,
                    4.853,
                    "Lyon — grande balade weekend",
                    "Encadrée salarié, places limitées — idéale pour tester résa + carte.",
                    "STAFF_GUIDED",
                    "Tour du lac + roseraie ~5 km",
                    1,
                    90,
                    55,
                    "PARK",
                    10,
                    1500,
                    2200),
            new DemoSpot(
                    "69001",
                    "Place Bellecour",
                    "Place Bellecour, 69002 Lyon",
                    45.7578,
                    4.8320,
                    "Lyon — communautaire adhérents",
                    "Balade gratuite entre membres autour de Bellecour.",
                    "COMMUNITY",
                    "Bellecour → Perrache ~3 km",
                    2,
                    50,
                    20,
                    "FLAT",
                    8,
                    0,
                    0));

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CityRepository cityRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final DogRepository dogRepository;
    private final DogDocumentRepository dogDocumentRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final WalkDifficultyCalculator difficultyCalculator;
    private final MessagingService messagingService;

    public DemoDataInitializer(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            PasswordEncoder passwordEncoder,
            CityRepository cityRepository,
            MembershipPlanRepository planRepository,
            MembershipRepository membershipRepository,
            DogRepository dogRepository,
            DogDocumentRepository dogDocumentRepository,
            WalkerProfileRepository walkerProfileRepository,
            MeetingPointRepository meetingPointRepository,
            WalkRepository walkRepository,
            BookingRepository bookingRepository,
            WalkDifficultyCalculator difficultyCalculator,
            MessagingService messagingService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.cityRepository = cityRepository;
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.dogRepository = dogRepository;
        this.dogDocumentRepository = dogDocumentRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.meetingPointRepository = meetingPointRepository;
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.difficultyCalculator = difficultyCalculator;
        this.messagingService = messagingService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long lyonId = cityRepository.search("69001", PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(City::getId)
                .orElseGet(() -> cityRepository.findAll().stream().findFirst().map(City::getId).orElse(null));

        // Un compte démo par rôle applicatif (mot de passe commun : Admin123!).
        User admin = upsertUser("admin@barkwalk.fr", Set.of(Role.ADMIN, Role.USER), "Admin", "BarkWalk", lyonId);
        User plainUser = upsertUser("user@barkwalk.fr", Set.of(Role.USER), "Jules", "Dupont", lyonId);
        User member = upsertUser("member@barkwalk.fr", Set.of(Role.USER, Role.MEMBER), "Léa", "Bernard", lyonId);
        User communityOrg = upsertUser(
                "org@barkwalk.fr", Set.of(Role.USER, Role.MEMBER, Role.COMMUNITY_ORG), "Camille", "Org", lyonId);
        User walker =
                upsertUser("walker@barkwalk.fr", Set.of(Role.USER, Role.WALKER_STAFF), "Sam", "Walker", lyonId);
        User walkerIndep =
                upsertUser("indep@barkwalk.fr", Set.of(Role.USER, Role.WALKER_INDEP), "Inès", "Indep", lyonId);
        // Compte historique multi-rôles (rétrocompat docs / recettes).
        User alice = upsertUser(
                "alice@example.com", Set.of(Role.USER, Role.MEMBER, Role.COMMUNITY_ORG), "Alice", "Martin", lyonId);

        enrichProfile(plainUser.getId(), "+33600000010", "NONE", 0, "PatteDouce");
        enrichProfile(member.getId(), "+33600000011", "NONE", 1, "NalaEtLea");
        enrichProfile(communityOrg.getId(), "+33600000012", "ACTIVE", 5, "BergesCamille");
        enrichProfile(alice.getId(), "+33600000001", "ACTIVE", 3, "FlairMartin");
        enrichProfile(walker.getId(), "+33600000013", "NONE", 0, "CapLaisse");
        enrichProfile(walkerIndep.getId(), "+33600000014", "NONE", 0, "CanicrossInes");
        enrichProfile(admin.getId(), "+33600000000", "NONE", 0, "GardienDuParc");

        User paul = upsertUser("paul@barkwalk.fr", Set.of(Role.USER, Role.MEMBER), "Paul", "Nguyen", lyonId);
        User sofia = upsertUser("sofia@barkwalk.fr", Set.of(Role.USER, Role.MEMBER), "Sofia", "Rossi", lyonId);
        User marc = upsertUser("marc@barkwalk.fr", Set.of(Role.USER, Role.MEMBER), "Marc", "Petit", lyonId);
        enrichProfile(paul.getId(), "+33600000021", "NONE", 2, "CaninPaul");
        enrichProfile(sofia.getId(), "+33600000022", "ELIGIBLE", 4, "SofiaPattes");
        enrichProfile(marc.getId(), "+33600000023", "NONE", 1, "MarcEtKira");

        Dog rex = ensureDemoDog(alice.getId(), "Rex", "Berger allemand", "LARGE", "HIGH");
        Dog nala = ensureDemoDog(member.getId(), "Nala", "Labrador", "LARGE", "MEDIUM");
        Dog milo = ensureDemoDog(communityOrg.getId(), "Milo", "Golden Retriever", "LARGE", "MEDIUM");
        Dog pixie = ensureDemoDog(plainUser.getId(), "Pixie", "Jack Russell", "SMALL", "HIGH");
        Dog kiwi = ensureDemoDog(paul.getId(), "Kiwi", "Border Collie", "MEDIUM", "HIGH");
        Dog luna = ensureDemoDog(sofia.getId(), "Luna", "Caniche", "SMALL", "LOW");
        Dog kira = ensureDemoDog(marc.getId(), "Kira", "Beagle", "MEDIUM", "MEDIUM");
        Dog oscar = ensureDemoDog(member.getId(), "Oscar", "Bouvier bernois", "LARGE", "LOW");

        ensureActiveMembership(member.getId());
        ensureActiveMembership(communityOrg.getId());
        ensureActiveMembership(alice.getId());
        ensureActiveMembership(paul.getId());
        ensureActiveMembership(sofia.getId());
        ensureActiveMembership(marc.getId());

        WalkerProfile walkerProfile = ensureWalkerProfile(
                walker.getId(), "STAFF", "Encadrant salarié Bark & Walk", new BigDecimal("4.80"), 12);
        WalkerProfile indepProfile = ensureWalkerProfile(
                walkerIndep.getId(), "INDEP", "Promeneur indépendant Bark & Walk", new BigDecimal("4.60"), 7);
        ensureIndepDocs(indepProfile);

        seedFranceWalks(walkerProfile, indepProfile, communityOrg, walker);
        seedLiveScenarios(
                lyonId,
                walkerProfile,
                indepProfile,
                communityOrg,
                walker,
                walkerIndep,
                List.of(
                        new Booker(alice, rex),
                        new Booker(member, nala),
                        new Booker(communityOrg, milo),
                        new Booker(paul, kiwi),
                        new Booker(sofia, luna),
                        new Booker(marc, kira),
                        new Booker(member, oscar),
                        new Booker(plainUser, pixie)));
    }

    private record Booker(User user, Dog dog) {}

    private void enrichProfile(Long userId, String phone, String communityOrgStatus, int completedWalks) {
        Profile profile = profileRepository.findById(userId).orElseThrow();
        profile.setPhone(phone);
        profile.setCommunityOrgStatus(communityOrgStatus);
        profile.setCompletedWalksCount(completedWalks);
        if (profile.getReviewStatus() == null || profile.getReviewStatus().isBlank()) {
            profile.setReviewStatus("VALIDATED");
        }
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private void enrichProfile(
            Long userId, String phone, String communityOrgStatus, int completedWalks, String pseudo) {
        Profile profile = profileRepository.findById(userId).orElseThrow();
        profile.setPhone(phone);
        profile.setCommunityOrgStatus(communityOrgStatus);
        profile.setCompletedWalksCount(completedWalks);
        if (pseudo != null && !pseudo.isBlank()) {
            profile.setPseudo(pseudo);
        }
        if (profile.getReviewStatus() == null || profile.getReviewStatus().isBlank()) {
            profile.setReviewStatus("VALIDATED");
        }
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private Dog ensureDemoDog(Long ownerId, String name, String breed, String size, String energy) {
        Optional<Dog> existing = dogRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
                .filter(d -> name.equalsIgnoreCase(d.getName()))
                .findFirst();
        if (existing.isPresent()) {
            Dog dog = existing.get();
            dog.setBreed(breed);
            dog.setSizeCategory(size);
            dog.setEnergyLevel(energy);
            dog.setDossierStatus("VALIDATED");
            dog.setUpdatedAt(Instant.now());
            Dog saved = dogRepository.save(dog);
            ensureValidatedRequiredDocs(saved);
            return saved;
        }
        Dog dog = new Dog();
        dog.setOwnerId(ownerId);
        dog.setName(name);
        dog.setBreed(breed);
        dog.setBirthDate(LocalDate.of(2020, 5, 12));
        dog.setSex("MALE");
        dog.setSterilized(true);
        dog.setWeightKg(weightForSize(size));
        dog.setSizeCategory(size);
        dog.setEnergyLevel(energy);
        dog.setSociabilityDogs("GOOD");
        dog.setSociabilityHumans("GOOD");
        dog.setDossierStatus("VALIDATED");
        Dog saved = dogRepository.save(dog);
        ensureValidatedRequiredDocs(saved);
        return saved;
    }

    private static BigDecimal weightForSize(String size) {
        return switch (size) {
            case "SMALL" -> new BigDecimal("7.50");
            case "MEDIUM" -> new BigDecimal("18.00");
            default -> new BigDecimal("28.50");
        };
    }

    private void ensureIndepDocs(WalkerProfile indep) {
        if (indep.getSiret() == null || indep.getSiret().isBlank()) {
            indep.setSiret("12345678900012");
        }
        if (indep.getRcProKey() == null || indep.getRcProKey().isBlank()) {
            indep.setRcProKey("demo/walkers/" + indep.getUserId() + "/rcpro-validated.pdf");
        }
        if (indep.getRcProExpiresAt() == null || indep.getRcProExpiresAt().isBefore(LocalDate.now())) {
            indep.setRcProExpiresAt(LocalDate.now().plusYears(1));
        }
        walkerProfileRepository.save(indep);
    }

    private void ensureActiveMembership(Long userId) {
        MembershipPlan monthly = planRepository.findByActiveTrue().stream()
                .filter(p -> "MONTHLY".equals(p.getCode()))
                .findFirst()
                .orElse(null);
        if (monthly == null
                || membershipRepository
                        .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE")
                        .isPresent()) {
            return;
        }
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setPlanId(monthly.getId());
        membership.setStatus("ACTIVE");
        membership.setCurrentPeriodStart(Instant.now().minus(5, ChronoUnit.DAYS));
        membership.setCurrentPeriodEnd(Instant.now().plus(25, ChronoUnit.DAYS));
        membershipRepository.save(membership);
    }

    private WalkerProfile ensureWalkerProfile(
            Long userId, String walkerType, String bio, BigDecimal rating, int ratingsCount) {
        return walkerProfileRepository.findByUserId(userId).orElseGet(() -> {
            WalkerProfile wp = new WalkerProfile();
            wp.setUserId(userId);
            wp.setWalkerType(walkerType);
            wp.setStatus("VALIDATED");
            wp.setBio(bio);
            wp.setAverageRating(rating);
            wp.setRatingsCount(ratingsCount);
            wp.setCharterSignedAt(Instant.now());
            return walkerProfileRepository.save(wp);
        });
    }

    private void seedFranceWalks(
            WalkerProfile staffWalker, WalkerProfile indepWalker, User communityOrg, User staffUser) {
        for (DemoSpot spot : FRANCE_SPOTS) {
            City city = cityRepository.search(spot.postalCode(), PageRequest.of(0, 1)).stream()
                    .findFirst()
                    .orElse(null);
            if (city == null) {
                continue;
            }

            MeetingPoint mp = meetingPointRepository.findByCityIdAndActiveTrue(city.getId()).stream()
                    .filter(p -> p.getName().equalsIgnoreCase(spot.meetingName()))
                    .findFirst()
                    .orElseGet(() -> {
                        MeetingPoint created = new MeetingPoint();
                        created.setCityId(city.getId());
                        created.setName(spot.meetingName());
                        created.setAddress(spot.address());
                        created.setLat(spot.lat());
                        created.setLng(spot.lng());
                        return meetingPointRepository.save(created);
                    });

            Walk walk = findDemoWalk(city.getId(), spot.title()).orElseGet(Walk::new);
            boolean isNew = walk.getId() == null;
            walk.setCityId(city.getId());
            walk.setMeetingPointId(mp.getId());
            walk.setWalkType(spot.walkType());
            walk.setTitle(spot.title());
            walk.setDescription(spot.description());
            walk.setDurationMinutes(spot.durationMinutes());
            int elevation = difficultyCalculator.normalizeElevation(spot.elevationGainM());
            String terrain = difficultyCalculator.normalizeTerrain(spot.terrainType());
            walk.setElevationGainM(elevation);
            walk.setTerrainType(terrain);
            walk.setDifficultyLevel(difficultyCalculator.compute(spot.durationMinutes(), elevation, terrain));
            walk.setCapacity(spot.capacity());
            walk.setPriceCents(Math.max(0, spot.priceCents()));
            walk.setNonMemberPriceCents(spot.nonMemberPriceCents() > 0 ? spot.nonMemberPriceCents() : null);
            walk.setRouteHint(spot.routeHint());
            walk.setUpdatedAt(Instant.now());

            if ("COMMUNITY".equals(spot.walkType())) {
                walk.setOrganizerUserId(communityOrg.getId());
                walk.setWalkerId(null);
                walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM"});
                walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM"});
                walk.setMinSociabilityDogs(null);
            } else if ("INDEP_GUIDED".equals(spot.walkType())) {
                walk.setOrganizerUserId(indepWalker.getUserId());
                walk.setWalkerId(indepWalker.getId());
                walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM", "LARGE"});
                walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM", "HIGH"});
                walk.setMinSociabilityDogs("GOOD");
            } else {
                walk.setOrganizerUserId(staffUser.getId());
                walk.setWalkerId(staffWalker.getId());
                walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM", "LARGE"});
                walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM", "HIGH"});
                walk.setMinSociabilityDogs("GOOD");
            }

            Instant startsAt = Instant.now().plus(spot.daysFromNow(), ChronoUnit.DAYS);
            // Ne pas écraser une balade déjà démarrée / terminée hors seed passé.
            if (isNew
                    || walk.getArchivedAt() == null
                            && !"IN_PROGRESS".equals(walk.getStatus())
                            && !"COMPLETED".equals(walk.getStatus())
                            && !"CANCELLED".equals(walk.getStatus())) {
                walk.setStartsAt(startsAt);
            } else if (walk.getStartsAt() == null) {
                walk.setStartsAt(startsAt);
            }

            if (spot.daysFromNow() < 0) {
                Instant end = startsAt.plus(spot.durationMinutes(), ChronoUnit.MINUTES);
                walk.setStartsAt(startsAt);
                walk.setStatus("COMPLETED");
                walk.setStartedAt(startsAt);
                walk.setCompletedAt(end);
                walk.setArchivedAt(end);
            } else if (isNew
                    || walk.getArchivedAt() == null
                            && !"IN_PROGRESS".equals(walk.getStatus())
                            && !"COMPLETED".equals(walk.getStatus())
                            && !"CANCELLED".equals(walk.getStatus())) {
                walk.setStatus("PUBLISHED");
                walk.setStartedAt(null);
                walk.setCompletedAt(null);
                walk.setArchivedAt(null);
            }

            Walk saved = walkRepository.save(walk);
            if (saved.getArchivedAt() == null) {
                messagingService.ensureWalkThread(saved);
            }
            archiveDemoDuplicates(spot.title(), saved.getId());
        }
    }

    private Optional<Walk> findDemoWalk(Long cityId, String title) {
        return walkRepository.findAll().stream()
                .filter(w -> title.equalsIgnoreCase(w.getTitle()))
                .filter(w -> w.getArchivedAt() == null || "COMPLETED".equals(w.getStatus()))
                .sorted((a, b) -> {
                    // Préfère la ville cible, puis le plus ancien id.
                    boolean aMatch = cityId.equals(a.getCityId());
                    boolean bMatch = cityId.equals(b.getCityId());
                    if (aMatch != bMatch) {
                        return aMatch ? -1 : 1;
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .findFirst();
    }

    /** Conserve une seule balade démo active par titre (évite les doublons multi-CP Lyon). */
    private void archiveDemoDuplicates(String title, Long keepId) {
        Instant now = Instant.now();
        walkRepository.findAll().stream()
                .filter(w -> title.equalsIgnoreCase(w.getTitle())
                        && !keepId.equals(w.getId())
                        && w.getArchivedAt() == null)
                .forEach(dup -> {
                    dup.setStatus("CANCELLED");
                    dup.setCancellationReason("demo_duplicate");
                    dup.setArchivedAt(now);
                    dup.setUpdatedAt(now);
                    walkRepository.save(dup);
                });
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
        if (profile.getReviewStatus() == null || profile.getReviewStatus().isBlank()) {
            profile.setReviewStatus("VALIDATED");
        }
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
        return user;
    }

    /** R2 : démo avec vaccination + assurance RC validées (sinon réservation bloquée). */
    private void ensureValidatedRequiredDocs(Dog dog) {
        var existing = dogDocumentRepository.findByDogId(dog.getId());
        for (String type : DogDossierService.REQUIRED_DOC_TYPES) {
            boolean present = existing.stream().anyMatch(d -> type.equals(d.getDocType()));
            if (present) {
                continue;
            }
            DogDocument doc = new DogDocument();
            doc.setDogId(dog.getId());
            doc.setDocType(type);
            doc.setStorageKey("demo/dogs/" + dog.getId() + "/" + type.toLowerCase() + "-validated.pdf");
            doc.setExpiresAt(LocalDate.now().plusYears(1));
            doc.setStatus("VALIDATED");
            dogDocumentRepository.save(doc);
        }
    }

    /**
     * Scénarios temps réel : pointage en attente, balade en cours, créneaux proches,
     * réservations CONFIRMED / PENDING_PAYMENT, traces GPS.
     */
    private void seedLiveScenarios(
            Long lyonId,
            WalkerProfile staffWalker,
            WalkerProfile indepWalker,
            User communityOrg,
            User staffUser,
            User indepUser,
            List<Booker> bookers) {
        if (lyonId == null || bookers.isEmpty()) {
            return;
        }

        MeetingPoint teteOr = ensureMeetingPoint(
                lyonId,
                "Parc de la Tête d'Or",
                "Boulevard des Belges, 69006 Lyon",
                45.781,
                4.853);
        MeetingPoint bellecour = ensureMeetingPoint(
                lyonId, "Place Bellecour", "Place Bellecour, 69002 Lyon", 45.7578, 4.8320);
        MeetingPoint croixRousse = ensureMeetingPoint(
                lyonId, "Place de la Croix-Rousse", "Place de la Croix-Rousse, 69004 Lyon", 45.7797, 4.8318);

        Instant now = Instant.now();

        // 1) Pointage à valider : finie il y a ~2 h, non archivée, sans attendanceValidatedAt.
        Walk rollcallWalk = upsertTimedWalk(
                lyonId,
                teteOr.getId(),
                "Lyon — pointage à valider",
                "Balade terminée : tester le rappel clignotant et le pointage des présents.",
                "STAFF_GUIDED",
                staffUser.getId(),
                staffWalker.getId(),
                now.minus(3, ChronoUnit.HOURS),
                60,
                "IN_PROGRESS",
                6,
                1500,
                2200,
                false);
        rollcallWalk.setStartedAt(rollcallWalk.getStartsAt());
        rollcallWalk.setCompletedAt(null);
        rollcallWalk.setArchivedAt(null);
        rollcallWalk.setAttendanceValidatedAt(null);
        rollcallWalk.setRouteGeojson(demoRouteAround(45.781, 4.853));
        rollcallWalk = walkRepository.save(rollcallWalk);
        messagingService.ensureWalkThread(rollcallWalk);
        ensureConfirmedBookings(rollcallWalk, bookers.subList(0, Math.min(4, bookers.size())), false);

        // 2) En cours maintenant (démarrée il y a 20 min).
        Walk liveWalk = upsertTimedWalk(
                lyonId,
                bellecour.getId(),
                "Lyon — balade en cours",
                "Balade live pour tester la carte GPS et le fil messagerie.",
                "STAFF_GUIDED",
                staffUser.getId(),
                staffWalker.getId(),
                now.minus(20, ChronoUnit.MINUTES),
                90,
                "IN_PROGRESS",
                8,
                1500,
                2200,
                false);
        liveWalk.setStartedAt(liveWalk.getStartsAt());
        liveWalk.setRouteGeojson(demoRouteAround(45.7578, 4.8320));
        liveWalk = walkRepository.save(liveWalk);
        messagingService.ensureWalkThread(liveWalk);
        ensureConfirmedBookings(liveWalk, bookers.subList(0, Math.min(3, bookers.size())), false);

        // 3) Aujourd'hui dans ~3 h — places encore ouvertes.
        Walk tonight = upsertTimedWalk(
                lyonId,
                croixRousse.getId(),
                "Lyon — ce soir Croix-Rousse",
                "Créneau proche pour tester une réservation de dernière minute.",
                "COMMUNITY",
                communityOrg.getId(),
                null,
                now.plus(3, ChronoUnit.HOURS),
                50,
                "PUBLISHED",
                8,
                0,
                0,
                true);
        tonight.setAcceptedSizes(new String[] {"SMALL", "MEDIUM", "LARGE"});
        tonight.setAcceptedEnergy(new String[] {"LOW", "MEDIUM", "HIGH"});
        tonight = walkRepository.save(tonight);
        messagingService.ensureWalkThread(tonight);
        ensureConfirmedBookings(tonight, bookers.subList(0, Math.min(2, bookers.size())), true);

        // 4) Demain matin — full pour file d'attente visuelle (capacité basse + résas).
        Walk almostFull = upsertTimedWalk(
                lyonId,
                teteOr.getId(),
                "Lyon — presque complète demain",
                "Capacité réduite : tester le compteur de places et les pseudos participants.",
                "STAFF_GUIDED",
                staffUser.getId(),
                staffWalker.getId(),
                now.plus(20, ChronoUnit.HOURS),
                75,
                "PUBLISHED",
                4,
                1500,
                2200,
                false);
        almostFull.setRouteGeojson(demoRouteAround(45.781, 4.853));
        almostFull = walkRepository.save(almostFull);
        messagingService.ensureWalkThread(almostFull);
        ensureConfirmedBookings(almostFull, bookers.subList(0, Math.min(3, bookers.size())), false);

        // 5) Indépendant + paiement en attente (non-membre).
        Walk indepSoon = upsertTimedWalk(
                lyonId,
                bellecour.getId(),
                "Lyon — indep paiement en attente",
                "Réservation PENDING_PAYMENT pour tester le parcours Stripe / expiration.",
                "INDEP_GUIDED",
                indepUser.getId(),
                indepWalker.getId(),
                now.plus(2, ChronoUnit.DAYS),
                60,
                "PUBLISHED",
                5,
                1800,
                2500,
                false);
        indepSoon = walkRepository.save(indepSoon);
        messagingService.ensureWalkThread(indepSoon);
        Booker nonMember = bookers.stream()
                .filter(b -> !b.user().getRoles().contains(Role.MEMBER))
                .findFirst()
                .orElse(bookers.getLast());
        ensureBooking(
                indepSoon,
                nonMember,
                "PENDING_PAYMENT",
                false,
                indepSoon.getNonMemberPriceCents() != null ? indepSoon.getNonMemberPriceCents() : 2500);

        // 6) Remplir aussi des balades catalogue FRANCE_SPOTS avec des résas.
        bookExistingPublishedWalks(bookers);

        // 7) Une balade passée déjà pointée (historique propre).
        Walk done = upsertTimedWalk(
                lyonId,
                teteOr.getId(),
                "Lyon — pointage déjà validé",
                "Historique : balade terminée et pointage déjà saisi.",
                "STAFF_GUIDED",
                staffUser.getId(),
                staffWalker.getId(),
                now.minus(2, ChronoUnit.DAYS),
                60,
                "COMPLETED",
                6,
                1500,
                2200,
                false);
        Instant doneEnd = done.getStartsAt().plus(60, ChronoUnit.MINUTES);
        done.setStartedAt(done.getStartsAt());
        done.setCompletedAt(doneEnd);
        done.setArchivedAt(doneEnd);
        done.setAttendanceValidatedAt(doneEnd.plus(15, ChronoUnit.MINUTES));
        done = walkRepository.save(done);
        ensureConfirmedBookings(done, bookers.subList(0, Math.min(2, bookers.size())), false);
        // Marquer ces résas comme COMPLETED (déjà pointées).
        for (Booking b : bookingRepository.findByWalkId(done.getId())) {
            if ("CONFIRMED".equals(b.getStatus())) {
                b.setStatus("COMPLETED");
                bookingRepository.save(b);
            }
        }
    }

    private MeetingPoint ensureMeetingPoint(Long cityId, String name, String address, double lat, double lng) {
        return meetingPointRepository.findByCityIdAndActiveTrue(cityId).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    MeetingPoint created = new MeetingPoint();
                    created.setCityId(cityId);
                    created.setName(name);
                    created.setAddress(address);
                    created.setLat(lat);
                    created.setLng(lng);
                    return meetingPointRepository.save(created);
                });
    }

    private Walk upsertTimedWalk(
            Long cityId,
            Long meetingPointId,
            String title,
            String description,
            String walkType,
            Long organizerUserId,
            Long walkerId,
            Instant startsAt,
            int durationMinutes,
            String status,
            int capacity,
            int priceCents,
            int nonMemberPriceCents,
            boolean community) {
        Walk walk = findDemoWalk(cityId, title).orElseGet(Walk::new);
        walk.setCityId(cityId);
        walk.setMeetingPointId(meetingPointId);
        walk.setWalkType(walkType);
        walk.setTitle(title);
        walk.setDescription(description);
        walk.setStartsAt(startsAt);
        walk.setDurationMinutes(durationMinutes);
        walk.setElevationGainM(community ? 20 : 40);
        walk.setTerrainType(community ? "FLAT" : "PARK");
        walk.setDifficultyLevel(difficultyCalculator.compute(durationMinutes, walk.getElevationGainM(), walk.getTerrainType()));
        walk.setCapacity(capacity);
        walk.setPriceCents(Math.max(0, priceCents));
        walk.setNonMemberPriceCents(nonMemberPriceCents > 0 ? nonMemberPriceCents : null);
        walk.setOrganizerUserId(organizerUserId);
        walk.setWalkerId(walkerId);
        walk.setStatus(status);
        walk.setAcceptedSizes(new String[] {"SMALL", "MEDIUM", "LARGE"});
        walk.setAcceptedEnergy(new String[] {"LOW", "MEDIUM", "HIGH"});
        walk.setMinSociabilityDogs(community ? null : "GOOD");
        walk.setRouteHint(community ? "Boucle urbaine ~3 km" : "Parc + berges ~4 km");
        walk.setUpdatedAt(Instant.now());
        if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status)) {
            walk.setArchivedAt(null);
        }
        if (!"IN_PROGRESS".equals(status) && !"COMPLETED".equals(status)) {
            walk.setStartedAt(null);
            walk.setCompletedAt(null);
        }
        Walk saved = walkRepository.save(walk);
        archiveDemoDuplicates(title, saved.getId());
        return saved;
    }

    private void ensureConfirmedBookings(Walk walk, List<Booker> bookers, boolean communityAck) {
        int slots = Math.min(walk.getCapacity(), bookers.size());
        for (int i = 0; i < slots; i++) {
            ensureBooking(walk, bookers.get(i), "CONFIRMED", communityAck, walk.getPriceCents());
        }
    }

    private void ensureBooking(Walk walk, Booker booker, String status, boolean communityAck, int priceCents) {
        List<Booking> forDog = bookingRepository.findByWalkId(walk.getId()).stream()
                .filter(b -> booker.dog().getId().equals(b.getDogId()))
                .toList();
        // Préférer une ligne active (l'index unique ignore seulement CANCELLED_*), sinon réutiliser une annulée.
        Booking booking = forDog.stream()
                .filter(b -> !"CANCELLED_FREE".equals(b.getStatus()) && !"CANCELLED_LATE".equals(b.getStatus()))
                .findFirst()
                .orElseGet(() -> forDog.isEmpty() ? new Booking() : forDog.getFirst());
        booking.setWalkId(walk.getId());
        booking.setUserId(booker.user().getId());
        booking.setDogId(booker.dog().getId());
        booking.setStatus(status);
        booking.setCommunityAck(communityAck);
        booking.setTrial(false);
        booking.setCancelledAt(null);
        booking.setCancelledByUserId(null);
        booking.setCancelledByRole(null);
        booking.setCancellationReason(null);
        booking.setPriceCents(Math.max(0, priceCents));
        booking.setCountsTowardOrgThreshold(true);
        booking.setCountsTowardMembershipQuota("CONFIRMED".equals(status) || "COMPLETED".equals(status));
        if ("CONFIRMED".equals(status) || "COMPLETED".equals(status)) {
            booking.setConfirmedAt(Instant.now().minus(1, ChronoUnit.HOURS));
            booking.setPaymentExpiresAt(null);
            booking.setPaymentRequired(false);
        } else if ("PENDING_PAYMENT".equals(status)) {
            booking.setConfirmedAt(null);
            booking.setPaymentExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
            booking.setPaymentRequired(true);
        } else {
            booking.setPaymentRequired(priceCents > 0);
        }
        bookingRepository.saveAndFlush(booking);
    }

    private void bookExistingPublishedWalks(List<Booker> bookers) {
        List<String> titles = List.of(
                "Balade matinale Tête d'Or",
                "Balade communautaire Bellecour",
                "Lyon — grande balade weekend",
                "Lyon — communautaire adhérents",
                "Buttes-Chaumont canin",
                "Balade Tuileries",
                "Balade Borély",
                "Nice Promenade",
                "Rennes Thabor");
        for (String title : titles) {
            Walk walk = walkRepository.findAll().stream()
                    .filter(w -> title.equalsIgnoreCase(w.getTitle()))
                    .filter(w -> w.getArchivedAt() == null)
                    .filter(w -> "PUBLISHED".equals(w.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (walk == null) {
                continue;
            }
            if (walk.getRouteGeojson() == null) {
                walk.setRouteGeojson(demoRouteAround(45.78, 4.85));
                walkRepository.save(walk);
            }
            int n = Math.min(2, Math.min(walk.getCapacity(), bookers.size()));
            ensureConfirmedBookings(walk, bookers.subList(0, n), "COMMUNITY".equals(walk.getWalkType()));
        }
    }

    private static Map<String, Object> demoRouteAround(double lat, double lng) {
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "LineString");
        List<List<Double>> coords = new ArrayList<>();
        coords.add(List.of(lng, lat));
        coords.add(List.of(lng + 0.004, lat + 0.002));
        coords.add(List.of(lng + 0.006, lat - 0.001));
        coords.add(List.of(lng + 0.002, lat - 0.003));
        coords.add(List.of(lng, lat));
        geo.put("coordinates", coords);
        return geo;
    }
}
