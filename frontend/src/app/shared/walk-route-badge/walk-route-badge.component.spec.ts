import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { WalkRouteBadgeComponent, hasWalkRouteTrack } from './walk-route-badge.component';

describe('hasWalkRouteTrack', () => {
  // Retourne false si la valeur est null.
  it('Retourne false pour une valeur null.', () => {
    expect(hasWalkRouteTrack(null)).toBe(false);
  });

  // Retourne false pour un objet vide.
  it('Retourne false pour un objet sans type LineString.', () => {
    expect(hasWalkRouteTrack({ type: 'Point', coordinates: [2.3, 48.8] } as any)).toBe(false);
  });

  // Retourne false pour une LineString avec moins de 2 points.
  it('Retourne false pour une LineString avec 1 seul point.', () => {
    expect(hasWalkRouteTrack({ type: 'LineString', coordinates: [[2.3, 48.8]] } as any)).toBe(false);
  });

  // Retourne true pour une LineString valide avec 2 points.
  it('Retourne true pour une LineString avec 2 points valides.', () => {
    expect(
      hasWalkRouteTrack({
        type: 'LineString',
        coordinates: [[2.3, 48.8], [2.35, 48.85]],
      }),
    ).toBe(true);
  });

  // Accepte une LineString encodée en JSON string.
  it('Retourne true pour une LineString en JSON string.', () => {
    const json = JSON.stringify({
      type: 'LineString',
      coordinates: [[2.3, 48.8], [2.35, 48.85]],
    });
    expect(hasWalkRouteTrack(json)).toBe(true);
  });

  // Retourne false pour un JSON invalide.
  it('Retourne false pour une chaîne JSON invalide.', () => {
    expect(hasWalkRouteTrack('not-json')).toBe(false);
  });
});

describe('WalkRouteBadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WalkRouteBadgeComponent],
    }).compileComponents();
  });

  // Vérifie la création du composant badge.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(WalkRouteBadgeComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // N'affiche pas le badge si la route est null.
  it("N'affiche pas le badge si routeGeojson est null.", () => {
    const fixture = TestBed.createComponent(WalkRouteBadgeComponent);
    fixture.componentInstance.routeGeojson = null;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.route-badge');
    expect(badge).toBeNull();
  });

  // Affiche le badge pour une LineString valide.
  it('Affiche le badge pour une LineString valide.', () => {
    const fixture = TestBed.createComponent(WalkRouteBadgeComponent);
    fixture.componentInstance.routeGeojson = {
      type: 'LineString',
      coordinates: [[2.3, 48.8], [2.35, 48.85]],
    };
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.route-badge');
    expect(badge).not.toBeNull();
  });
});
