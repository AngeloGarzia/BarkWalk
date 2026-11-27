import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { PhotoUploaderComponent } from './photo-uploader.component';
import { AuthService } from '../../core/auth.service';

const authStub = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  isAdmin: vi.fn().mockReturnValue(false),
  hasRole: vi.fn().mockReturnValue(false),
  getToken: vi.fn().mockReturnValue(null),
  getRefreshToken: vi.fn().mockReturnValue(null),
  loggedIn: signal(true),
  roles: signal<string[]>([]),
  userId: signal<number | null>(1),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  ensureFreshAccessToken: vi.fn(),
  accessTokenTtlMs: vi.fn().mockReturnValue(null),
  isAccessExpiringSoon: vi.fn().mockReturnValue(true),
};

describe('PhotoUploaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PhotoUploaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création du composant d'upload photo.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(PhotoUploaderComponent);
    fixture.componentInstance.uploadUrl = '/api/test/photos';
    expect(fixture.componentInstance).toBeTruthy();
  });

  // La liste de photos est vide par défaut.
  it('Aucune photo chargée par défaut.', () => {
    const fixture = TestBed.createComponent(PhotoUploaderComponent);
    fixture.componentInstance.uploadUrl = '/api/test/photos';
    fixture.detectChanges();
    const comp = fixture.componentInstance as any;
    expect(comp.photos()).toEqual([]);
  });

  // initialPhotos peuple la liste.
  it('Charge les photos initiales via le setter initialPhotos.', () => {
    const fixture = TestBed.createComponent(PhotoUploaderComponent);
    fixture.componentInstance.uploadUrl = '/api/test/photos';
    fixture.componentInstance.initialPhotos = [
      { id: 1, url: 'https://example.com/img.jpg', sortOrder: 0 },
    ];
    fixture.detectChanges();
    const comp = fixture.componentInstance as any;
    expect(comp.photos().length).toBe(1);
  });
});
