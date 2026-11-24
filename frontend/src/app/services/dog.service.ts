import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Dog, DogRequest } from '../models/dog';

@Injectable({ providedIn: 'root' })
export class DogService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/dogs';

  list(): Observable<Dog[]> {
    return this.http.get<Dog[]>(this.baseUrl);
  }

  create(payload: DogRequest): Observable<Dog> {
    return this.http.post<Dog>(this.baseUrl, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
