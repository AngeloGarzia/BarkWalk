import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DogService } from './dog.service';

describe('DogService', () => {
  let service: DogService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DogService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // Charge la liste des chiens du propriétaire.
  it('liste les chiens', () => {
    let result: unknown;
    service.list().subscribe((dogs) => (result = dogs));
    const req = http.expectOne('/api/dogs');
    req.flush([]);
    expect(result).toEqual([]);
  });

  // Crée un chien.
  it('crée un chien', () => {
    service
      .create({
        name: 'Nala',
        breed: 'Labrador',
        birthDate: '2020-01-01',
        sex: 'FEMALE',
        sterilized: true,
        weightKg: 28,
        sizeCategory: 'LARGE',
        energyLevel: 'MEDIUM',
        sociabilityDogs: 'GOOD',
        sociabilityHumans: 'GOOD',
      } as never)
      .subscribe();
    const req = http.expectOne('/api/dogs');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, name: 'Nala' });
  });

  // Supprime un chien.
  it('supprime un chien', () => {
    service.delete(4).subscribe();
    const req = http.expectOne('/api/dogs/4');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
