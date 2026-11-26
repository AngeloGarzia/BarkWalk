package com.barkwalk.walk.service;

import com.barkwalk.dog.domain.Dog;
import com.barkwalk.walk.domain.Walk;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// R3 : vérifie taille, énergie et sociabilité min ; renvoie un motif précis si incompatible.
@Service
public class DogWalkCompatibilityService {

    private static final Map<String, Integer> SOCIABILITY_RANK = Map.of(
            "UNKNOWN", 0,
            "FRAGILE", 1,
            "AVERAGE", 2,
            "GOOD", 3);

    public DogWalkCompatibility evaluate(Walk walk, Dog dog) {
        if (!containsIgnoreCase(walk.getAcceptedSizes(), dog.getSizeCategory())) {
            return DogWalkCompatibility.incompatible(
                    "Size " + dog.getSizeCategory() + " not accepted for this walk");
        }
        if (!containsIgnoreCase(walk.getAcceptedEnergy(), dog.getEnergyLevel())) {
            return DogWalkCompatibility.incompatible(
                    "Energy " + dog.getEnergyLevel() + " not accepted for this walk");
        }
        String minSoc = walk.getMinSociabilityDogs();
        if (minSoc != null && !minSoc.isBlank()) {
            int dogRank = rank(dog.getSociabilityDogs());
            int minRank = rank(minSoc);
            if (dogRank < minRank) {
                return DogWalkCompatibility.incompatible(
                        "Sociability " + dog.getSociabilityDogs() + " below required " + minSoc);
            }
        }
        return DogWalkCompatibility.ok();
    }

    public void assertCompatible(Walk walk, Dog dog) {
        DogWalkCompatibility result = evaluate(walk, dog);
        if (!result.compatible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.reason());
        }
    }

    private static int rank(String level) {
        if (level == null) {
            return 0;
        }
        Integer rank = SOCIABILITY_RANK.get(level.trim().toUpperCase(Locale.ROOT));
        return rank != null ? rank : 0;
    }

    private static boolean containsIgnoreCase(String[] values, String value) {
        if (values == null || value == null) {
            return false;
        }
        return Arrays.stream(values).anyMatch(v -> v != null && v.equalsIgnoreCase(value));
    }
}
