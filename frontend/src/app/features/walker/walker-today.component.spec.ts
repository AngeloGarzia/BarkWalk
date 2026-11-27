import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { WalkerTodayComponent } from './walker-today.component';

describe('WalkerTodayComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WalkerTodayComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach((r) => r.flush([]));
  });

  // Vérifie la création du composant agenda encadrant.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(WalkerTodayComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // statusLabel() retourne les libellés corrects pour chaque statut.
  it('statusLabel() retourne le libellé FR du statut.', () => {
    const fixture = TestBed.createComponent(WalkerTodayComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.statusLabel('PUBLISHED')).toBe('Planifiée');
    expect(comp.statusLabel('IN_PROGRESS')).toBe('En cours');
    expect(comp.statusLabel('COMPLETED')).toBe('Terminée');
    expect(comp.statusLabel('CANCELLED')).toBe('Annulée');
  });
});
