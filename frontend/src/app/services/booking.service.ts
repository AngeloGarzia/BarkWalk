// Client HTTP pour les balades et les réservations de l'utilisateur connecté.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Booking, BookingRequest, Walk } from '../models/booking';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  walk(id: number): Observable<Walk> {
    return this.http.get<Walk>(`/api/walks/${id}`);
  }

  mine(): Observable<Booking[]> {
    return this.http.get<Booking[]>('/api/bookings');
  }

  create(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>('/api/bookings', request);
  }

  cancel(id: number): Observable<Booking> {
    return this.http.post<Booking>(`/api/bookings/${id}/cancel`, {});
  }
}
