// Appels API de recherche de communes.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface City {
  id: number;
  name: string;
  postalCode: string;
}

@Injectable({ providedIn: 'root' })
export class CityService {
  private readonly http = inject(HttpClient);

  search(q: string): Observable<City[]> {
    return this.http.get<City[]>('/api/cities', { params: { q } });
  }
}
