import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { CatalogComponent } from './catalog.component';
import { AuthService } from '../../core/auth.service';
import { AttendanceReminderService } from '../../core/attendance-reminder.service';

// CatalogComponent intègre WalksMapComponent qui utilise MapLibre.
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

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(false),
  isAdmin: vi.fn().mockReturnValue(false),
  hasRole: vi.fn().mockReturnValue(false),
  getToken: vi.fn().mockReturnValue(null),
  getRefreshToken: vi.fn().mockReturnValue(null),
  loggedIn: signal(false),
  roles: signal<string[]>([]),
  userId: signal<number | null>(null),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  ensureFreshAccessToken: vi.fn(),
  accessTokenTtlMs: vi.fn().mockReturnValue(null),
  isAccessExpiringSoon: vi.fn().mockReturnValue(true),
};

const attendanceStub = {
  pending: signal<unknown[]>([]),
  pendingCount: signal(0),
  loading: signal(false),
  refresh: vi.fn(),
  ngOnDestroy: vi.fn(),
};

describe('CatalogComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: AttendanceReminderService, useValue: attendanceStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création du catalogue de balades.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(CatalogComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
