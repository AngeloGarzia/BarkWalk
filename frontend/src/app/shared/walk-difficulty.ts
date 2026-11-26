// Miroir UI du calcul serveur WalkDifficultyCalculator (durée + dénivelé + terrain → 1..10).

export const TERRAIN_OPTIONS = [
  { value: 'FLAT', label: 'Plat / urbain' },
  { value: 'PARK', label: 'Parc / allée' },
  { value: 'FOREST', label: 'Forêt' },
  { value: 'TRAIL', label: 'Sentier' },
  { value: 'MOUNTAIN', label: 'Montagne / rocaille' },
] as const;

export type TerrainType = (typeof TERRAIN_OPTIONS)[number]['value'];

export function terrainLabel(terrain?: string | null): string {
  const found = TERRAIN_OPTIONS.find((t) => t.value === terrain);
  return found?.label ?? terrain ?? 'Terrain';
}

export function estimateDifficultyLevel(
  durationMinutes: number,
  elevationGainM: number,
  terrainType: string,
): number {
  const durationScore = durationScoreOf(durationMinutes);
  const elevationScore = elevationScoreOf(elevationGainM);
  const terrainScore = terrainScoreOf(terrainType);
  const raw = durationScore * 0.4 + elevationScore * 0.4 + terrainScore * 0.2;
  const rounded = Math.round(raw);
  return Math.max(1, Math.min(10, rounded === 0 ? 1 : rounded));
}

export function difficultyLabel(level: number): string {
  if (level <= 2) {
    return 'Très facile';
  }
  if (level <= 4) {
    return 'Facile';
  }
  if (level <= 6) {
    return 'Modérée';
  }
  if (level <= 8) {
    return 'Soutenue';
  }
  return 'Exigeante';
}

function durationScoreOf(minutes: number): number {
  if (minutes <= 20) return 1;
  if (minutes <= 35) return 2;
  if (minutes <= 50) return 3;
  if (minutes <= 70) return 5;
  if (minutes <= 90) return 6;
  if (minutes <= 120) return 8;
  return 10;
}

function elevationScoreOf(meters: number): number {
  if (meters <= 20) return 1;
  if (meters <= 60) return 2;
  if (meters <= 120) return 4;
  if (meters <= 200) return 6;
  if (meters <= 350) return 8;
  return 10;
}

function terrainScoreOf(terrain: string): number {
  switch (terrain) {
    case 'FLAT':
      return 1;
    case 'PARK':
      return 2;
    case 'FOREST':
      return 4;
    case 'TRAIL':
      return 7;
    case 'MOUNTAIN':
      return 10;
    default:
      return 1;
  }
}
