import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { CityPickerComponent } from '../../shared/city-picker/city-picker.component';
import { City } from '../../shared/city.service';

export interface Walk {
  id: number;
  title: string;
  description: string;
  walkType: string;
  cityId: number;
  durationMinutes: number;
  priceCents: number;
  capacity: number;
  bookedCount: number;
  startsAt: string;
  status: string;
}

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [CityPickerComponent, RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="catalog-page">
      <header class="page-header">
        <h1>Promenades disponibles</h1>
        <p class="subtitle">Sélectionnez votre ville pour découvrir les promeneurs.</p>
      </header>

      <div class="filter-bar">
        <app-city-picker (citySelected)="onCity($event)" />
      </div>

      @if (loading()) {
        <p class="state-msg">Chargement…</p>
      } @else if (!selectedCity()) {
        <div class="empty-state">
          <span class="empty-icon">🗺️</span>
          <p>Cherchez une ville pour voir les promenades disponibles.</p>
        </div>
      } @else if (walks().length === 0) {
        <div class="empty-state">
          <span class="empty-icon">😔</span>
          <p>Aucune promenade disponible à <strong>{{ selectedCity()?.name }}</strong> pour le moment.</p>
        </div>
      } @else {
        <ul class="walk-list">
          @for (walk of walks(); track walk.id) {
            <li class="walk-card">
              <div class="walk-info">
                <h3>{{ walk.title }}</h3>
                <p>{{ walk.description }}</p>
                <div class="tags">
                  <span class="tag">{{ walk.walkType }}</span>
                  <span class="tag">{{ walk.durationMinutes }} min</span>
                  <span class="tag">{{ walk.bookedCount }}/{{ walk.capacity }}</span>
                </div>
              </div>
              <div class="walk-meta">
                <span class="price">{{ (walk.priceCents / 100) | number: '1.0-2' }} €</span>
                <span class="walker">{{ walk.startsAt | date: 'short' }}</span>
                <a class="btn-book" routerLink="/booking" [queryParams]="{ walkId: walk.id }">Réserver</a>
              </div>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [
    `
      .catalog-page {
        max-width: 820px;
        margin: 0 auto;
        padding: 1.5rem 1rem;
      }
      .page-header {
        margin-bottom: 1.5rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
      }
      .subtitle {
        color: var(--muted);
        margin: 0;
      }
      .filter-bar {
        margin-bottom: 1.75rem;
        max-width: 360px;
      }
      .state-msg {
        color: var(--muted);
        text-align: center;
        padding: 2rem 0;
      }
      .empty-state {
        text-align: center;
        padding: 3rem 1rem;
        color: var(--muted);
      }
      .empty-icon {
        font-size: 3rem;
        display: block;
        margin-bottom: 0.75rem;
      }
      .walk-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .walk-card {
        background: white;
        border-radius: 12px;
        padding: 1.25rem;
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      }
      .walk-info {
        flex: 1;
      }
      .walk-info h3 {
        margin: 0 0 0.35rem;
        font-size: 1rem;
      }
      .walk-info p {
        margin: 0 0 0.6rem;
        color: var(--muted);
        font-size: 0.88rem;
        line-height: 1.4;
      }
      .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
      }
      .tag {
        display: inline-block;
        background: var(--bg);
        border-radius: 999px;
        padding: 2px 10px;
        font-size: 0.8rem;
        color: var(--accent);
        font-weight: 600;
      }
      .walk-meta {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.3rem;
        min-width: 90px;
      }
      .price {
        font-size: 1.3rem;
        font-weight: 700;
        color: var(--accent);
        font-family: var(--font-display);
      }
      .walker {
        font-size: 0.82rem;
        color: var(--muted);
      }
      .btn-book {
        margin-top: 0.4rem;
        background: var(--accent);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 0.4rem 0.9rem;
        font-size: 0.85rem;
        font-weight: 600;
        cursor: pointer;
        transition: opacity 0.15s;
      }
      .btn-book:hover {
        opacity: 0.85;
      }
    `,
  ],
})
export class CatalogComponent {
  private readonly http = inject(HttpClient);

  protected readonly walks = signal<Walk[]>([]);
  protected readonly loading = signal(false);
  protected readonly selectedCity = signal<City | null>(null);

  protected onCity(city: City): void {
    this.selectedCity.set(city);
    this.loading.set(true);
    this.http.get<Walk[]>('/api/walks', { params: { cityId: city.id.toString() } }).subscribe({
      next: walks => {
        this.walks.set(walks);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
