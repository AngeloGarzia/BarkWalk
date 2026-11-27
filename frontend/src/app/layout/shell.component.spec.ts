import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ShellComponent } from './shell.component';
import { AuthService } from '../core/auth.service';
import { AttendanceReminderService } from '../core/attendance-reminder.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(false),
  isAdmin: vi.fn().mockReturnValue(false),
  isCommunityOrg: vi.fn().mockReturnValue(false),
  canAccessCalendar: vi.fn().mockReturnValue(false),
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

describe('ShellComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        { provide: AttendanceReminderService, useValue: attendanceStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création de la coquille applicative.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // La navigation inférieure est rendue.
  it('Rend la barre de navigation inférieure.', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const nav = fixture.nativeElement.querySelector('nav');
    expect(nav).not.toBeNull();
  });

  // Logo global : lien vers l’accueil.
  it('Affiche le logo Bark & Walk vers l’accueil.', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const brand = fixture.nativeElement.querySelector('a.brand');
    expect(brand).not.toBeNull();
    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.textContent).toContain('BARK');
  });
});
