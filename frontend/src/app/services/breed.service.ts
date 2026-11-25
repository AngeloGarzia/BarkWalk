// Catalogue de races via Dog API (https://dogapi.dog) — gratuit, sans clé.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

interface DogApiBreed {
  id: string;
  type: string;
  attributes: { name: string };
}

interface DogApiBreedsResponse {
  data: DogApiBreed[];
}

const EXTRA_BREEDS = ['Croisé / Mixte', 'Race inconnue'];

@Injectable({ providedIn: 'root' })
export class BreedService {
  private readonly http = inject(HttpClient);

  private readonly breeds$: Observable<string[]> = this.http
    .get<DogApiBreedsResponse>('https://dogapi.dog/api/v2/breeds', {
      params: { 'page[size]': '1000' },
    })
    .pipe(
      map((res) => {
        const names = (res.data ?? [])
          .map((b) => b.attributes?.name?.trim())
          .filter((n): n is string => !!n);
        return [...EXTRA_BREEDS, ...names].sort((a, b) =>
          a.localeCompare(b, 'fr', { sensitivity: 'base' }),
        );
      }),
      catchError(() => of([...EXTRA_BREEDS])),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

  listBreeds(): Observable<string[]> {
    return this.breeds$;
  }
}
