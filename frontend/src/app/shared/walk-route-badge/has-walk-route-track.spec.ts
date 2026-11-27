import { describe, expect, it } from 'vitest';
import { hasWalkRouteTrack } from './walk-route-badge.component';

describe('hasWalkRouteTrack', () => {
  // Accepte une LineString avec au moins deux points.
  it('détecte une trace GPS valide', () => {
    expect(
      hasWalkRouteTrack({
        type: 'LineString',
        coordinates: [
          [4.8, 45.7],
          [4.81, 45.71],
        ],
      }),
    ).toBe(true);
  });

  // Refuse un point unique ou un type incorrect.
  it('refuse une géométrie insuffisante', () => {
    expect(hasWalkRouteTrack({ type: 'LineString', coordinates: [[4.8, 45.7]] })).toBe(false);
    expect(hasWalkRouteTrack({ type: 'Point', coordinates: [4.8, 45.7] })).toBe(false);
    expect(hasWalkRouteTrack(null)).toBe(false);
  });
});
