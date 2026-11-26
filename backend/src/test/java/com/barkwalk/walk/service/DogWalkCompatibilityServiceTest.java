package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barkwalk.dog.domain.Dog;
import com.barkwalk.walk.domain.Walk;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DogWalkCompatibilityServiceTest {

    private final DogWalkCompatibilityService service = new DogWalkCompatibilityService();

    @Test
    void evaluate_compatibleWhenAllCriteriaMatch() {
        Walk walk = walk(new String[] {"LARGE"}, new String[] {"HIGH"}, "AVERAGE");
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        DogWalkCompatibility result = service.evaluate(walk, dog);
        assertThat(result.compatible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void evaluate_rejectsSizeWithPreciseReason() {
        Walk walk = walk(new String[] {"SMALL"}, new String[] {"HIGH"}, null);
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        DogWalkCompatibility result = service.evaluate(walk, dog);
        assertThat(result.compatible()).isFalse();
        assertThat(result.reason()).contains("Size");
    }

    @Test
    void evaluate_rejectsEnergyWithPreciseReason() {
        Walk walk = walk(new String[] {"LARGE"}, new String[] {"LOW"}, null);
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        DogWalkCompatibility result = service.evaluate(walk, dog);
        assertThat(result.compatible()).isFalse();
        assertThat(result.reason()).contains("Energy");
    }

    @Test
    void evaluate_rejectsSociabilityWithPreciseReason() {
        Walk walk = walk(new String[] {"LARGE"}, new String[] {"HIGH"}, "GOOD");
        Dog dog = dog("LARGE", "HIGH", "FRAGILE");
        DogWalkCompatibility result = service.evaluate(walk, dog);
        assertThat(result.compatible()).isFalse();
        assertThat(result.reason()).contains("Sociability");
    }

    @Test
    void assertCompatible_throwsBadRequest() {
        Walk walk = walk(new String[] {"SMALL"}, new String[] {"HIGH"}, null);
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        assertThatThrownBy(() -> service.assertCompatible(walk, dog))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void evaluate_rejectsCategory1OnNormalWalk() {
        Walk walk = walk(new String[] {"LARGE"}, new String[] {"HIGH"}, null);
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        dog.setDangerCategory("1");
        DogWalkCompatibility result = service.evaluate(walk, dog);
        assertThat(result.compatible()).isFalse();
        assertThat(result.reason()).contains("dedicated");
    }

    @Test
    void evaluate_allowsCategory2OnDedicatedWalk() {
        Walk walk = walk(new String[] {"LARGE"}, new String[] {"HIGH"}, null);
        walk.setAllowsDangerCategories(true);
        Dog dog = dog("LARGE", "HIGH", "GOOD");
        dog.setDangerCategory("2");
        assertThat(service.evaluate(walk, dog).compatible()).isTrue();
    }

    private static Walk walk(String[] sizes, String[] energy, String minSoc) {
        Walk walk = new Walk();
        walk.setAcceptedSizes(sizes);
        walk.setAcceptedEnergy(energy);
        walk.setMinSociabilityDogs(minSoc);
        walk.setAllowsDangerCategories(false);
        return walk;
    }

    private static Dog dog(String size, String energy, String sociability) {
        Dog dog = new Dog();
        dog.setSizeCategory(size);
        dog.setEnergyLevel(energy);
        dog.setSociabilityDogs(sociability);
        return dog;
    }
}
