// Catalogue filtré par ville sélectionnée.
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
  cityName: string | null;
  meetingPointId: number;
  meetingPointName: string | null;
  meetingPointAddress: string | null;
  lat: number | null;
  lng: number | null;
  durationMinutes: number;
  priceCents: number;
  nonMemberPriceCents: number | null;
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
        max-width: 28rem;
        margin: 0 auto;
        padding: 1.1rem 1rem 1.5rem;
      }
      .page-header {
        margin-bottom: 1.1rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
        color: var(--forest-deep);
        font-size: 1.45rem;
      }
      .subtitle {
        color: var(--muted);
        margin: 0;
        font-size: 0.92rem;
      }
      .filter-bar {
        margin-bottom: 1.25rem;
        max-width: 100%;
      }
      .state-msg {
        color: var(--muted);
        text-align: center;
        padding: 2rem 0;
      }
      .empty-state {
        text-align: center;
        padding: 2.5rem 1rem;
        color: var(--muted);
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
      }
      .empty-icon {
        font-size: 2rem;
        display: block;
        margin-bottom: 0.75rem;
      }
      .walk-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
      }
      .walk-card {
        background: var(--surface);
        border-radius: var(--radius-md);
        border: 1px solid var(--line);
        padding: 1.05rem;
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        box-shadow: var(--shadow-sm);
      }
      .walk-info {
        flex: 1;
        min-width: 0;
      }
      .walk-info h3 {
        margin: 0 0 0.35rem;
        font-size: 1.02rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
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
        background: var(--forest-soft);
        border-radius: var(--radius-pill);
        padding: 0.2rem 0.65rem;
        font-size: 0.75rem;
        color: var(--forest);
        font-weight: 600;
      }
      .walk-meta {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.35rem;
        min-width: 90px;
      }
      .price {
        font-size: 1.15rem;
        font-weight: 800;
        color: var(--forest);
        font-family: var(--font-display);
      }
      .walker {
        font-size: 0.78rem;
        color: var(--muted);
        text-align: right;
      }
      .btn-book {
        margin-top: 0.25rem;
        background: var(--cta);
        color: var(--on-cta);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.5rem 0.95rem;
        font-size: 0.85rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
        text-decoration: none;
        box-shadow: 0 4px 12px rgba(232, 135, 42, 0.25);
        transition: background 0.15s;
      }
      .btn-book:hover {
        background: var(--cta-hover);
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
