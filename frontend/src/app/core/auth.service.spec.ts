import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  // Indique l’absence de session au démarrage.
  it('signale déconnecté sans jeton', () => {
    expect(service.isLoggedIn()).toBe(false);
  });

  // Persiste les jetons après login.
  it('stocke la session au login', () => {
    service.login({ email: 'a@b.c', password: 'x' }).subscribe();
    const req = http.expectOne('/api/auth/login');
    req.flush({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresInMinutes: 15,
      roles: ['USER', 'ADMIN'],
    });

    expect(service.isLoggedIn()).toBe(true);
    expect(service.isAdmin()).toBe(true);
    expect(localStorage.getItem('bw_access_token')).toBe('access');
    expect(localStorage.getItem('bw_refresh_token')).toBe('refresh');
  });

  // Efface la session locale au logout.
  it('vide le stockage au logout', () => {
    service.login({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresInMinutes: 15,
      roles: ['USER'],
    });

    service.logout();
    http.expectOne('/api/auth/logout').flush({});
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('bw_access_token')).toBeNull();
  });
});
