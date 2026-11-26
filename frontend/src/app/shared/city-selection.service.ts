// Ville choisie partagée entre catalogue et carte d’accueil.
import { Injectable, signal } from '@angular/core';
import { City } from './city.service';

@Injectable({ providedIn: 'root' })
export class CitySelectionService {
  readonly selectedCity = signal<City | null>(null);

  setCity(city: City | null): void {
    this.selectedCity.set(city);
  }
}
