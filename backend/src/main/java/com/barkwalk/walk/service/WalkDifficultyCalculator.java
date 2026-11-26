package com.barkwalk.walk.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calcule la difficulté d'une balade (1–10) à partir de la durée, du dénivelé et du terrain.
 *
 * <p>Pondération indicative : durée ~40 %, dénivelé ~40 %, terrain ~20 %.
 */
@Component
public class WalkDifficultyCalculator {

    public static final Set<String> TERRAIN_TYPES = Set.of("FLAT", "PARK", "FOREST", "TRAIL", "MOUNTAIN");

    public String normalizeTerrain(String terrainType) {
        if (terrainType == null || terrainType.isBlank()) {
            return "FLAT";
        }
        String normalized = terrainType.trim().toUpperCase(Locale.ROOT);
        if (!TERRAIN_TYPES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "terrainType must be one of " + TERRAIN_TYPES);
        }
        return normalized;
    }

    public int normalizeElevation(Integer elevationGainM) {
        int value = elevationGainM == null ? 0 : elevationGainM;
        if (value < 0 || value > 3000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "elevationGainM must be between 0 and 3000");
        }
        return value;
    }

    public int compute(int durationMinutes, int elevationGainM, String terrainType) {
        String terrain = normalizeTerrain(terrainType);
        int elevation = normalizeElevation(elevationGainM);

        double durationScore = durationScore(durationMinutes); // 0..10
        double elevationScore = elevationScore(elevation); // 0..10
        double terrainScore = terrainScore(terrain); // 0..10

        double raw = durationScore * 0.40 + elevationScore * 0.40 + terrainScore * 0.20;
        int rounded = (int) Math.round(raw);
        return Math.max(1, Math.min(10, rounded == 0 ? 1 : rounded));
    }

    private static double durationScore(int minutes) {
        if (minutes <= 20) {
            return 1;
        }
        if (minutes <= 35) {
            return 2;
        }
        if (minutes <= 50) {
            return 3;
        }
        if (minutes <= 70) {
            return 5;
        }
        if (minutes <= 90) {
            return 6;
        }
        if (minutes <= 120) {
            return 8;
        }
        return 10;
    }

    private static double elevationScore(int meters) {
        if (meters <= 20) {
            return 1;
        }
        if (meters <= 60) {
            return 2;
        }
        if (meters <= 120) {
            return 4;
        }
        if (meters <= 200) {
            return 6;
        }
        if (meters <= 350) {
            return 8;
        }
        return 10;
    }

    private static double terrainScore(String terrain) {
        return switch (terrain) {
            case "FLAT" -> 1;
            case "PARK" -> 2;
            case "FOREST" -> 4;
            case "TRAIL" -> 7;
            case "MOUNTAIN" -> 10;
            default -> 1;
        };
    }
}
