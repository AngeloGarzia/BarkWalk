import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

describe('adminGuard', () => {
  let auth: { isLoggedIn: ReturnType<typeof vi.fn>; isAdmin: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { isLoggedIn: vi.fn(), isAdmin: vi.fn() };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  // Autorise un administrateur connecté.
  it('autorise un admin connecté', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.isAdmin.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  // Renvoie vers login si aucune session.
  it('redirige vers /login sans session', () => {
    auth.isLoggedIn.mockReturnValue(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/login']));
  });

  // Renvoie à l’accueil si le compte n’est pas admin.
  it('redirige vers / pour un non-admin', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.isAdmin.mockReturnValue(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/']));
  });
});
