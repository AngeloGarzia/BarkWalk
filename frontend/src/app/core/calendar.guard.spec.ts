import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { calendarGuard } from './calendar.guard';
import { AuthService } from './auth.service';

describe('calendarGuard', () => {
  let auth: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    canAccessCalendar: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    auth = { isLoggedIn: vi.fn(), canAccessCalendar: vi.fn() };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  // Autorise admin / organisateur connecté.
  it('autorise un compte éligible', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.canAccessCalendar.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() => calendarGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  // Renvoie vers login sans session.
  it('redirige vers /login sans session', () => {
    auth.isLoggedIn.mockReturnValue(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => calendarGuard({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/login']));
  });

  // Renvoie à l’accueil si le rôle n’est pas autorisé.
  it('redirige vers / pour un compte non autorisé', () => {
    auth.isLoggedIn.mockReturnValue(true);
    auth.canAccessCalendar.mockReturnValue(false);
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => calendarGuard({} as never, {} as never));
    expect(result).toEqual(router.createUrlTree(['/']));
  });
});
