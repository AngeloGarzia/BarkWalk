import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { BookingComponent } from './booking.component';
import { AuthService } from '../../core/auth.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  isAdmin: vi.fn().mockReturnValue(false),
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

const activatedRouteStub = {
  snapshot: {
    queryParamMap: {
      get: vi.fn().mockReturnValue(null),
    },
  },
};

describe('BookingComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création du composant de réservation.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(BookingComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // isCancellable() identifie correctement les réservations annulables.
  it('isCancellable() retourne true pour CONFIRMED et PENDING_PAYMENT.', () => {
    const fixture = TestBed.createComponent(BookingComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.isCancellable({ status: 'CONFIRMED' })).toBe(true);
    expect(comp.isCancellable({ status: 'PENDING_PAYMENT' })).toBe(true);
    expect(comp.isCancellable({ status: 'CANCELLED_FREE' })).toBe(false);
  });
});
