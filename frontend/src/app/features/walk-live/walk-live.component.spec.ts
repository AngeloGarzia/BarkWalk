import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

// WalkLiveComponent utilise MapLibre directement.
vi.mock('maplibre-gl', () => ({
  Map: vi.fn().mockImplementation(() => ({
    on: vi.fn().mockReturnThis(),
    once: vi.fn().mockImplementation((_e: string, cb: () => void) => cb()),
    addControl: vi.fn(),
    addSource: vi.fn(),
    addLayer: vi.fn(),
    getSource: vi.fn().mockReturnValue({ setData: vi.fn() }),
    remove: vi.fn(),
    resize: vi.fn(),
    flyTo: vi.fn(),
    fitBounds: vi.fn(),
    getZoom: vi.fn().mockReturnValue(14),
    easeTo: vi.fn(),
  })),
  Marker: vi.fn().mockImplementation(() => ({
    setLngLat: vi.fn().mockReturnThis(),
    setPopup: vi.fn().mockReturnThis(),
    addTo: vi.fn().mockReturnThis(),
    remove: vi.fn(),
    getElement: vi.fn().mockReturnValue(document.createElement('div')),
  })),
  NavigationControl: vi.fn(),
  Popup: vi.fn().mockImplementation(() => ({ setHTML: vi.fn().mockReturnThis() })),
  LngLatBounds: vi.fn().mockImplementation(() => ({ extend: vi.fn().mockReturnThis() })),
  setWorkerUrl: vi.fn(),
  GeoJSONSource: vi.fn(),
}));

import { WalkLiveComponent } from './walk-live.component';
import { AuthService } from '../../core/auth.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  isAdmin: vi.fn().mockReturnValue(false),
  hasRole: vi.fn().mockReturnValue(false),
  getToken: vi.fn().mockReturnValue(null),
  getRefreshToken: vi.fn().mockReturnValue(null),
  loggedIn: signal(true),
  roles: signal<string[]>(['WALKER_STAFF']),
  userId: signal<number | null>(5),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  ensureFreshAccessToken: vi.fn(),
  accessTokenTtlMs: vi.fn().mockReturnValue(null),
  isAccessExpiringSoon: vi.fn().mockReturnValue(true),
};

const activatedRouteStub = {
  snapshot: { paramMap: { get: vi.fn().mockReturnValue('42') } },
};

describe('WalkLiveComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WalkLiveComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((r) => r.flush(null));
  });

  // Vérifie la création du suivi GPS live.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(WalkLiveComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // formatDistance() formate correctement les distances.
  it('formatDistance() formate les distances en m et km.', () => {
    const fixture = TestBed.createComponent(WalkLiveComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.formatDistance(500)).toBe('500 m');
    expect(comp.formatDistance(1500)).toBe('1.5 km');
    expect(comp.formatDistance(12000)).toBe('12 km');
  });

  // statusLabel() retourne les libellés des statuts de balade.
  it('statusLabel() retourne les libellés corrects.', () => {
    const fixture = TestBed.createComponent(WalkLiveComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.statusLabel('IN_PROGRESS')).toBe('En cours');
    expect(comp.statusLabel('COMPLETED')).toBe('Terminée');
  });
});
