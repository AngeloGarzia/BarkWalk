import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { HomeComponent } from './home.component';

vi.mock('maplibre-gl', () => ({
  Map: vi.fn().mockImplementation(() => ({
    addControl: vi.fn(),
    on: vi.fn(),
    remove: vi.fn(),
    getCanvas: () => ({ style: {} }),
  })),
  NavigationControl: vi.fn(),
  Marker: vi.fn().mockImplementation(() => ({
    setLngLat: vi.fn().mockReturnThis(),
    addTo: vi.fn().mockReturnThis(),
    remove: vi.fn(),
  })),
}));

describe('HomeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: () => false,
            loggedIn: () => false,
            roles: () => [],
          },
        },
      ],
    });
  });

  // Instancie la page d’accueil.
  it('crée le composant', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
