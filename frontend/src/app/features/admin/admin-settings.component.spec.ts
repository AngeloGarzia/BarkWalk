import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AdminSettingsComponent } from './admin-settings.component';

describe('AdminSettingsComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Absorber les requêtes du ngOnInit
    httpMock.match(() => true).forEach((r) => r.flush([]));
  });

  // Vérifie la création du composant paramètres admin.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(AdminSettingsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
