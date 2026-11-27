import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CityService } from './city.service';

describe('CityService', () => {
  let service: CityService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CityService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // Interroge l’API communes avec le terme saisi.
  it('recherche les communes', () => {
    let result: unknown;
    service.search('Lyon').subscribe((cities) => (result = cities));

    const req = http.expectOne((r) => r.url === '/api/cities' && r.params.get('q') === 'Lyon');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Lyon', postalCode: '69001' }]);
    expect(result).toEqual([{ id: 1, name: 'Lyon', postalCode: '69001' }]);
  });
});
