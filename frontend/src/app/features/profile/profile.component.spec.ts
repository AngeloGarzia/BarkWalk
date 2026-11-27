import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';

// ProfileComponent intègre CommunityWalkModalComponent qui utilise MapLibre via RouteEditor/MapPicker.
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
    getZoom: vi.fn().mockReturnValue(10),
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

import { ProfileComponent } from './profile.component';
import { AuthService } from '../../core/auth.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  isAdmin: vi.fn().mockReturnValue(false),
  isCommunityOrg: vi.fn().mockReturnValue(false),
  canAccessCalendar: vi.fn().mockReturnValue(false),
  hasRole: vi.fn().mockReturnValue(false),
  getToken: vi.fn().mockReturnValue(null),
  getRefreshToken: vi.fn().mockReturnValue(null),
  loggedIn: signal(true),
  roles: signal<string[]>(['USER']),
  userId: signal<number | null>(1),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  ensureFreshAccessToken: vi.fn(),
  accessTokenTtlMs: vi.fn().mockReturnValue(null),
  isAccessExpiringSoon: vi.fn().mockReturnValue(true),
};

describe('ProfileComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Absorber les requêtes du ngOnInit pour éviter les erreurs de vérification
    httpMock.match(() => true).forEach((r) => r.flush(null));
  });

  // Vérifie la création du composant profil.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
