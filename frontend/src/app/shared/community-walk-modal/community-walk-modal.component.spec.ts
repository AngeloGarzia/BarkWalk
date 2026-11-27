import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';

// CommunityWalkModal intègre MeetingPointMapPickerComponent et RouteEditorComponent
// qui utilisent tous deux MapLibre.
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
    getZoom: vi.fn().mockReturnValue(13),
    easeTo: vi.fn(),
  })),
  Marker: vi.fn().mockImplementation(() => ({
    setLngLat: vi.fn().mockReturnThis(),
    setPopup: vi.fn().mockReturnThis(),
    addTo: vi.fn().mockReturnThis(),
    remove: vi.fn(),
    getElement: vi.fn().mockReturnValue(document.createElement('div')),
    getLngLat: vi.fn().mockReturnValue({ lng: 2.3, lat: 48.8 }),
    on: vi.fn().mockReturnThis(),
  })),
  NavigationControl: vi.fn(),
  Popup: vi.fn().mockImplementation(() => ({
    setHTML: vi.fn().mockReturnThis(),
    setLngLat: vi.fn().mockReturnThis(),
  })),
  LngLatBounds: vi.fn().mockImplementation(() => ({ extend: vi.fn().mockReturnThis() })),
  setWorkerUrl: vi.fn(),
  GeoJSONSource: vi.fn(),
}));

import { CommunityWalkModalComponent } from './community-walk-modal.component';
import { AuthService } from '../../core/auth.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  isAdmin: vi.fn().mockReturnValue(false),
  hasRole: vi.fn().mockReturnValue(false),
  getToken: vi.fn().mockReturnValue(null),
  getRefreshToken: vi.fn().mockReturnValue(null),
  loggedIn: signal(true),
  roles: signal<string[]>(['COMMUNITY_ORG']),
  userId: signal<number | null>(1),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  ensureFreshAccessToken: vi.fn(),
  accessTokenTtlMs: vi.fn().mockReturnValue(null),
  isAccessExpiringSoon: vi.fn().mockReturnValue(true),
};

describe('CommunityWalkModalComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommunityWalkModalComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((r) => r.flush([]));
  });

  // Vérifie la création de la modale de balade communautaire.
  it('Se crée sans erreur avec une ville fournie.', () => {
    const fixture = TestBed.createComponent(CommunityWalkModalComponent);
    fixture.componentInstance.city = {
      id: 1,
      name: 'Lyon',
      postalCode: '69001',
      lat: 45.75,
      lng: 4.85,
    };
    fixture.componentInstance.walk = null;
    expect(fixture.componentInstance).toBeTruthy();
  });

  // Le formulaire est invalide à l'initialisation (titre requis).
  it("Formulaire invalide à l'initialisation (titre manquant).", () => {
    const fixture = TestBed.createComponent(CommunityWalkModalComponent);
    fixture.componentInstance.city = {
      id: 1,
      name: 'Lyon',
      postalCode: '69001',
      lat: 45.75,
      lng: 4.85,
    };
    fixture.componentInstance.walk = null;
    httpMock.match('/api/meeting-points').forEach((r) => r.flush([]));
    const comp = fixture.componentInstance as any;
    expect(comp.form.invalid).toBe(true);
  });
});
