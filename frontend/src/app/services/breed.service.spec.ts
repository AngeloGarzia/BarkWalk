import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BreedService } from './breed.service';

describe('BreedService', () => {
  let service: BreedService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BreedService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // Enrichit le catalogue distant avec les races locales.
  it('ajoute les races de repli au catalogue', () => {
    let names: string[] = [];
    service.listBreeds().subscribe((list) => (names = list));
    const req = http.expectOne((r) => r.url.startsWith('https://dogapi.dog/api/v2/breeds'));
    req.flush({
      data: [{ id: '1', type: 'breed', attributes: { name: 'Beagle' } }],
    });
    expect(names).toContain('Beagle');
    expect(names).toContain('Croisé / Mixte');
  });

  // Conserve un minimum de races si l’API distante échoue.
  it('retombe sur les races locales en erreur', () => {
    let names: string[] = [];
    service.listBreeds().subscribe((list) => (names = list));
    http.expectOne((r) => r.url.startsWith('https://dogapi.dog/api/v2/breeds')).flush('err', {
      status: 500,
      statusText: 'Error',
    });
    expect(names).toEqual(['Croisé / Mixte', 'Race inconnue']);
  });
});
