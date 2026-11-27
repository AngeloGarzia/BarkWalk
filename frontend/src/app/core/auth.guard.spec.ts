import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let auth: { isLoggedIn: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { isLoggedIn: vi.fn() };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  // Autorise l’accès si une session est active.
  it('autorise un utilisateur connecté', () => {
    auth.isLoggedIn.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  // Redirige vers la page de connexion sinon.
  it('redirige vers /login sans session', () => {
    auth.isLoggedIn.mockReturnValue(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/login']));
  });
});
