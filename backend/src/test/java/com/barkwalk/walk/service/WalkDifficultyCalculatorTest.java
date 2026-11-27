package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WalkDifficultyCalculatorTest {

    private final WalkDifficultyCalculator calculator = new WalkDifficultyCalculator();

    // Calcule difficulté basse pour une balade plate et courte.
    @Test
    void compute_easyFlatShortWalk_isLow() {
        int level = calculator.compute(30, 10, "FLAT");
        assertThat(level).isBetween(1, 3);
    }

    // Calcule difficulté haute pour une longue balade en montagne.
    @Test
    void compute_longMountainWalk_isHigh() {
        int level = calculator.compute(130, 450, "MOUNTAIN");
        assertThat(level).isBetween(8, 10);
    }

    // Refuse un type de terrain inconnu.
    @Test
    void normalizeTerrain_rejectsUnknown() {
        assertThatThrownBy(() -> calculator.normalizeTerrain("SWAMP"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // Terrain vide → FLAT ; dénivelé hors bornes refusé.
    @Test
    void normalize_defaultsAndBounds() {
        assertThat(calculator.normalizeTerrain(null)).isEqualTo("FLAT");
        assertThat(calculator.normalizeTerrain("  ")).isEqualTo("FLAT");
        assertThat(calculator.normalizeElevation(null)).isZero();
        assertThatThrownBy(() -> calculator.normalizeElevation(-1))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> calculator.normalizeElevation(4000))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(calculator.compute(20, 0, "PARK")).isBetween(1, 3);
        assertThat(calculator.compute(100, 100, "TRAIL")).isBetween(4, 8);
    }
}
