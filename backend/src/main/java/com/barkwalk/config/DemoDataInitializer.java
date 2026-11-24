package com.barkwalk.config;

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
import com.barkwalk.city.domain.City;
import com.barkwalk.city.repository.CityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "barkwalk.demo.seed-users", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

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
        Long lyonId = cityRepository.search("69001", org.springframework.data.domain.PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(City::getId)
                .orElseGet(() -> cityRepository.findAll().stream().findFirst().map(City::getId).orElse(null));

        User admin = upsertUser("admin@barkwalk.fr", Set.of(Role.ADMIN, Role.USER), "Admin", "BarkWalk", lyonId);
        User alice = upsertUser("alice@example.com", Set.of(Role.USER, Role.MEMBER, Role.COMMUNITY_ORG), "Alice", "Martin", lyonId);
        User walker = upsertUser("walker@barkwalk.fr", Set.of(Role.USER, Role.WALKER_STAFF), "Sam", "Walker", lyonId);

        Profile aliceProfile = profileRepository.findById(alice.getId()).orElseThrow();
        aliceProfile.setCompletedWalksCount(3);
        aliceProfile.setCommunityOrgEnabled(true);
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
        if (monthly != null && membershipRepository
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

        if (lyonId != null && meetingPointRepository.findByCityIdAndActiveTrue(lyonId).isEmpty()) {
            MeetingPoint mp = new MeetingPoint();
            mp.setCityId(lyonId);
            mp.setName("Parc de la Tête d'Or");
            mp.setAddress("Boulevard des Belges, 69006 Lyon");
            mp.setLat(45.781);
            mp.setLng(4.853);
            mp = meetingPointRepository.save(mp);

            if (walkRepository.count() == 0) {
                Walk walk = new Walk();
                walk.setCityId(lyonId);
                walk.setMeetingPointId(mp.getId());
                walk.setWalkType("STAFF_GUIDED");
                walk.setTitle("Balade matinale Tête d'Or");
                walk.setDescription("Balade encadrée pour chiens sociables.");
                walk.setStartsAt(Instant.now().plus(2, ChronoUnit.DAYS));
                walk.setDurationMinutes(60);
                walk.setCapacity(8);
                walk.setPriceCents(0);
                walk.setNonMemberPriceCents(1500);
                walk.setWalkerId(walkerProfile.getId());
                walk.setStatus("PUBLISHED");
                walk.setAcceptedSizes("SMALL,MEDIUM,LARGE");
                walk.setAcceptedEnergy("LOW,MEDIUM,HIGH");
                walk.setMinSociabilityDogs("GOOD");
                walk.setRouteHint("Tour du lac ~4 km");
                walkRepository.save(walk);

                MeetingPoint bellecour = new MeetingPoint();
                bellecour.setCityId(lyonId);
                bellecour.setName("Place Bellecour");
                bellecour.setAddress("Place Bellecour, 69002 Lyon");
                bellecour.setLat(45.7578);
                bellecour.setLng(4.8320);
                bellecour = meetingPointRepository.save(bellecour);

                Walk community = new Walk();
                community.setCityId(lyonId);
                community.setMeetingPointId(bellecour.getId());
                community.setWalkType("COMMUNITY");
                community.setTitle("Balade communautaire Bellecour");
                community.setDescription("Balade entre adhérents — non encadrée.");
                community.setStartsAt(Instant.now().plus(3, ChronoUnit.DAYS));
                community.setDurationMinutes(45);
                community.setCapacity(6);
                community.setPriceCents(0);
                community.setOrganizerUserId(alice.getId());
                community.setStatus("PUBLISHED");
                community.setAcceptedSizes("SMALL,MEDIUM");
                community.setAcceptedEnergy("LOW,MEDIUM");
                community.setCommunityDisclaimerRequired(true);
                community.setRouteHint("Berges du Rhône");
                walkRepository.save(community);
            }
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
