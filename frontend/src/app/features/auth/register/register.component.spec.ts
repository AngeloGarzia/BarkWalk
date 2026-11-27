import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/auth.service';

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

describe('RegisterComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création du formulaire d'inscription.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // Le formulaire est invalide sans données.
  it("Formulaire invalide à l'initialisation.", () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.form.invalid).toBe(true);
  });

  // Le formulaire est valide avec toutes les données requises.
  it('Formulaire valide avec toutes les données requises.', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const comp = fixture.componentInstance as any;
    comp.form.setValue({
      firstName: 'Jean',
      lastName: 'Dupont',
      email: 'jean@example.fr',
      password: 'Password1!',
      acceptCgu: true,
    });
    expect(comp.form.valid).toBe(true);
  });
});
