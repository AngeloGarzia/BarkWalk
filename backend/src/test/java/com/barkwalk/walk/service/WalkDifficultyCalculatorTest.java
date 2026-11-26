package com.barkwalk.walk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WalkDifficultyCalculatorTest {

    private final WalkDifficultyCalculator calculator = new WalkDifficultyCalculator();

    @Test
    void compute_easyFlatShortWalk_isLow() {
        int level = calculator.compute(30, 10, "FLAT");
        assertThat(level).isBetween(1, 3);
    }

    @Test
    void compute_longMountainWalk_isHigh() {
        int level = calculator.compute(130, 450, "MOUNTAIN");
        assertThat(level).isBetween(8, 10);
    }

    @Test
    void normalizeTerrain_rejectsUnknown() {
        assertThatThrownBy(() -> calculator.normalizeTerrain("SWAMP"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
