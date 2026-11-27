import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CitySelectionService } from './city-selection.service';

describe('CitySelectionService', () => {
  let service: CitySelectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CitySelectionService);
  });

  // Mémorise la commune choisie pour le catalogue et la carte.
  it('enregistre la ville sélectionnée', () => {
    service.setCity({ id: 1, name: 'Lyon', postalCode: '69001' });
    expect(service.selectedCity()?.name).toBe('Lyon');
  });

  // Efface la sélection de commune.
  it('réinitialise la sélection', () => {
    service.setCity({ id: 1, name: 'Lyon', postalCode: '69001' });
    service.setCity(null);
    expect(service.selectedCity()).toBeNull();
  });
});
