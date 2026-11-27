import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { MessagingComponent } from './messaging.component';
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

describe('MessagingComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessagingComponent],
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

  // Vérifie la création du composant messagerie.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(MessagingComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // typeLabel() retourne les libellés corrects.
  it('typeLabel() retourne le libellé du type de conversation.', () => {
    const fixture = TestBed.createComponent(MessagingComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.typeLabel('WALK')).toBe('Balade');
    expect(comp.typeLabel('DIRECT')).toBe('Privé');
    expect(comp.typeLabel('SUPPORT')).toBe('Support');
  });
});
