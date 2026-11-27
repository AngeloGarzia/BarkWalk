import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { DogsComponent } from './dogs.component';
import { BreedService } from '../../services/breed.service';

const breedStub = {
  listBreeds: vi.fn().mockReturnValue(of(['Labrador', 'Berger Allemand'])),
};

describe('DogsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DogsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BreedService, useValue: breedStub },
      ],
    }).compileComponents();
  });

  // Vérifie la création du composant chiens.
  it('Se crée sans erreur.', () => {
    const fixture = TestBed.createComponent(DogsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  // sizeLabel() retourne les libellés corrects.
  it('sizeLabel() retourne les libellés des tailles.', () => {
    const fixture = TestBed.createComponent(DogsComponent);
    const comp = fixture.componentInstance as any;
    expect(comp.sizeLabel('SMALL')).toBe('Petit');
    expect(comp.sizeLabel('MEDIUM')).toBe('Moyen');
    expect(comp.sizeLabel('LARGE')).toBe('Grand');
  });
});
