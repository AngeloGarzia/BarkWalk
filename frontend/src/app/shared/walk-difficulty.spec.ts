import { describe, expect, it } from 'vitest';
import { difficultyLabel, estimateDifficultyLevel, terrainLabel } from './walk-difficulty';

describe('walk-difficulty', () => {
  // Calcule un score bas pour un parcours plat et court.
  it('estime un niveau faible sur plat court', () => {
    expect(estimateDifficultyLevel(30, 10, 'FLAT')).toBeLessThanOrEqual(3);
  });

  // Calcule un score élevé pour un parcours montagneux long.
  it('estime un niveau élevé en montagne', () => {
    expect(estimateDifficultyLevel(130, 400, 'MOUNTAIN')).toBeGreaterThanOrEqual(8);
  });

  // Borne le score entre 1 et 10.
  it('borne le niveau entre 1 et 10', () => {
    expect(estimateDifficultyLevel(0, 0, 'FLAT')).toBe(1);
    expect(estimateDifficultyLevel(999, 9999, 'MOUNTAIN')).toBe(10);
  });

  // Traduit le code terrain pour l’affichage.
  it('libellé terrain connu', () => {
    expect(terrainLabel('PARK')).toBe('Parc / allée');
  });

  // Classe le niveau en libellé lisible.
  it('libellé de difficulté', () => {
    expect(difficultyLabel(2)).toBe('Très facile');
    expect(difficultyLabel(5)).toBe('Modérée');
    expect(difficultyLabel(9)).toBe('Exigeante');
  });
});
