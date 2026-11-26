// Catalogue filtré par ville sélectionnée + carte des balades.
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CityPickerComponent } from '../../shared/city-picker/city-picker.component';
import { City } from '../../shared/city.service';
import { CitySelectionService } from '../../shared/city-selection.service';
import {
  CommunityWalkEditModel,
  CommunityWalkModalComponent,
} from '../../shared/community-walk-modal/community-walk-modal.component';
import { RouteGeoJson } from '../../shared/route-editor/route-editor.component';
import { WalkDifficultyBarComponent } from '../../shared/walk-difficulty-bar/walk-difficulty-bar.component';
import { WalksMapComponent } from '../../shared/walks-map/walks-map.component';

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
  elevationGainM?: number;
  terrainType?: string;
  difficultyLevel?: number;
  organizerDisplayName?: string | null;
  promoPhotoUrls?: string[] | null;
  recycledFromWalkId?: number | null;
  galleryCount?: number;
  priceCents: number;
  nonMemberPriceCents: number | null;
  capacity: number;
  bookedCount: number;
  startsAt: string;
  status: string;
  organizerUserId?: number | null;
  acceptedSizes?: string[];
  acceptedEnergy?: string[];
  minSociabilityDogs?: string | null;
  routeHint?: string | null;
  routeGeojson?: RouteGeoJson | string | null;
}

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [
    CityPickerComponent,
    WalksMapComponent,
    CommunityWalkModalComponent,
    WalkDifficultyBarComponent,
    RouterLink,
    DatePipe,
    DecimalPipe,
  ],
  template: `
    <div class="catalog-page">
      <header class="page-header">
        <div class="page-header-row">
          <div>
            <h1>Promenades disponibles</h1>
            <p class="subtitle">Sélectionnez votre ville pour découvrir les promeneurs.</p>
          </div>
          @if (canCreateCommunityWalk()) {
            <button type="button" class="btn-create" (click)="openCreateModal()">
              Proposer une balade
            </button>
          }
        </div>
      </header>

      <div class="filter-bar">
        <app-city-picker [initialCity]="selectedCity()" (citySelected)="onCity($event)" />
      </div>

      @if (createHint()) {
        <p class="banner warn" role="status">{{ createHint() }}</p>
      }

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
          <p>
            Aucune promenade disponible à <strong>{{ selectedCity()?.name }}</strong> pour le
            moment.
          </p>
        </div>
      } @else {
        <section class="map-block" aria-label="Carte des balades à {{ selectedCity()?.name }}">
          <div class="map-head">
            <h2>Sur la carte</h2>
            <p class="muted">Pins autour de {{ selectedCity()?.name }}</p>
          </div>
          <app-walks-map
            [walks]="walks()"
            [fallbackCenter]="cityCenter()"
            [cityFocus]="true"
            [selectedWalkId]="pulsedWalkId()"
            (pinSelected)="onPinSelected($event)"
          />
        </section>

        <ul class="walk-list">
          @for (walk of walks(); track walk.id) {
            <li
              class="walk-card"
              [class.is-pulse]="pulsedWalkId() === walk.id"
              [attr.data-walk-id]="walk.id"
              (click)="onPinSelected(walk.id)"
            >
              @if (walk.promoPhotoUrls?.[0]; as thumb) {
                <img class="walk-thumb" [src]="thumb" [alt]="walk.title" />
              }
              <div class="walk-info">
                <h3>{{ walk.title }}</h3>
                <p>{{ walk.description }}</p>
                <div class="tags">
                  <span class="tag">{{ walk.walkType }}</span>
                  @if (walk.organizerDisplayName) {
                    <span class="tag">{{ walk.organizerDisplayName }}</span>
                  }
                  <span class="tag">{{ walk.durationMinutes }} min</span>
                  @if (walk.difficultyLevel) {
                    <span class="tag tag-diff">
                      <app-walk-difficulty-bar [level]="walk.difficultyLevel" />
                    </span>
                  }
                  <span class="tag">{{ walk.bookedCount }}/{{ walk.capacity }}</span>
                </div>
              </div>
              <div class="walk-meta">
                <span class="price">{{ (walk.priceCents / 100) | number: '1.0-2' }} €</span>
                <span class="walker">{{ walk.startsAt | date: 'short' }}</span>
                @if (canEditWalk(walk)) {
                  <button
                    type="button"
                    class="btn-edit"
                    (click)="openEditModal(walk); $event.stopPropagation()"
                  >
                    Modifier
                  </button>
                }
                <a
                  class="btn-book"
                  routerLink="/booking"
                  [queryParams]="{ walkId: walk.id }"
                  (click)="$event.stopPropagation()"
                  >Réserver</a
                >
              </div>
            </li>
          }
        </ul>
      }
    </div>

    @if (createOpen() && selectedCity(); as city) {
      <app-community-walk-modal
        [city]="city"
        [walk]="editingWalk()"
        (closed)="closeCreateModal()"
        (saved)="onWalkModalSaved()"
      />
    }
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
      .page-header-row {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
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
        margin-bottom: 1rem;
        max-width: 100%;
      }
      .banner.warn {
        margin: 0 0 0.85rem;
        padding: 0.55rem 0.7rem;
        border-radius: var(--radius-sm);
        background: #f8efe3;
        color: var(--warn);
        font-size: 0.88rem;
      }
      .btn-create {
        flex-shrink: 0;
        white-space: nowrap;
        background: var(--forest);
        color: var(--on-forest);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.55rem 1.1rem;
        font-size: 0.9rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
        box-shadow: var(--shadow-sm);
      }
      .btn-create:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-create:hover:not(:disabled) {
        filter: brightness(1.05);
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
      .map-block {
        margin-bottom: 1.1rem;
      }
      .map-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.05rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .map-head .muted {
        margin: 0 0 0.55rem;
        font-size: 0.85rem;
        color: var(--muted);
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
      .walk-thumb {
        width: 4.5rem;
        height: 4.5rem;
        object-fit: cover;
        border-radius: var(--radius-sm);
        flex-shrink: 0;
        background: var(--bg);
      }
      .walk-card.is-pulse {
        animation: walk-tile-pulse 1.7s ease-in-out 3;
        border-color: var(--forest);
      }
      @keyframes walk-tile-pulse {
        0%,
        100% {
          box-shadow: var(--shadow-sm);
          filter: brightness(1);
        }
        50% {
          box-shadow:
            0 0 0 3px rgba(47, 93, 50, 0.22),
            0 10px 28px rgba(47, 93, 50, 0.18);
          filter: brightness(1.04);
        }
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
      .btn-edit {
        margin-top: 0.15rem;
        background: transparent;
        color: var(--forest);
        border: 1px solid var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.4rem 0.85rem;
        font-size: 0.8rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-edit:hover {
        background: var(--forest-soft);
      }
    `,
  ],
})
export class CatalogComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly citySelection = inject(CitySelectionService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly cdr = inject(ChangeDetectorRef);
  private pulseTimer?: ReturnType<typeof setTimeout>;

  protected readonly walks = signal<Walk[]>([]);
  protected readonly loading = signal(false);
  protected readonly selectedCity = signal<City | null>(null);
  protected readonly pulsedWalkId = signal<number | null>(null);

  protected readonly createOpen = signal(false);
  protected readonly editingWalk = signal<CommunityWalkEditModel | null>(null);
  protected readonly createHint = signal<string | null>(null);

  protected readonly canCreateCommunityWalk = computed(() => {
    const roles = this.auth.roles();
    return roles.includes('COMMUNITY_ORG') || roles.includes('ADMIN');
  });

  protected readonly cityCenter = computed(() => {
    const city = this.selectedCity();
    if (
      city?.lat == null ||
      city.lng == null ||
      !Number.isFinite(city.lat) ||
      !Number.isFinite(city.lng)
    ) {
      return null;
    }
    return { lat: city.lat, lng: city.lng };
  });

  ngOnInit(): void {
    const shared = this.citySelection.selectedCity();
    if (shared) {
      this.onCity(shared);
    }
  }

  ngOnDestroy(): void {
    if (this.pulseTimer) {
      clearTimeout(this.pulseTimer);
    }
  }

  protected onCity(city: City): void {
    this.selectedCity.set(city);
    this.citySelection.setCity(city);
    this.createHint.set(null);
    this.closeCreateModal();
    this.reloadWalks(city.id);
  }

  protected onPinSelected(walkId: number): void {
    this.pulsedWalkId.set(walkId);
    this.cdr.detectChanges();
    if (this.pulseTimer) {
      clearTimeout(this.pulseTimer);
    }
    requestAnimationFrame(() => {
      const tile = this.host.nativeElement.querySelector(
        `[data-walk-id="${walkId}"]`,
      ) as HTMLElement | null;
      tile?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    this.pulseTimer = setTimeout(() => this.pulsedWalkId.set(null), 5200);
  }

  protected canEditWalk(walk: Walk): boolean {
    if (walk.status === 'CANCELLED' || walk.status === 'COMPLETED') {
      return false;
    }
    const uid = this.auth.userId();
    if (this.auth.isAdmin()) {
      return true;
    }
    return uid != null && walk.organizerUserId === uid;
  }

  protected openCreateModal(): void {
    if (!this.canCreateCommunityWalk()) {
      return;
    }
    if (!this.selectedCity()) {
      this.createHint.set('Sélectionnez d’abord une ville pour proposer une balade.');
      return;
    }
    this.createHint.set(null);
    this.editingWalk.set(null);
    this.createOpen.set(true);
  }

  protected openEditModal(walk: Walk): void {
    if (!this.canEditWalk(walk)) {
      return;
    }
    const city = this.selectedCity();
    if (!city || city.id !== walk.cityId) {
      this.createHint.set('Sélectionnez la ville de la balade pour la modifier.');
      return;
    }
    this.createHint.set(null);
    this.editingWalk.set({
      id: walk.id,
      cityId: walk.cityId,
      title: walk.title,
      description: walk.description,
      meetingPointId: walk.meetingPointId,
      lat: walk.lat,
      lng: walk.lng,
      startsAt: walk.startsAt,
      durationMinutes: walk.durationMinutes,
      elevationGainM: walk.elevationGainM ?? 0,
      terrainType: walk.terrainType ?? 'PARK',
      difficultyLevel: walk.difficultyLevel ?? null,
      capacity: walk.capacity,
      acceptedSizes: walk.acceptedSizes,
      acceptedEnergy: walk.acceptedEnergy,
      minSociabilityDogs: walk.minSociabilityDogs,
      routeHint: walk.routeHint,
      routeGeojson: walk.routeGeojson,
    });
    this.createOpen.set(true);
  }

  protected closeCreateModal(): void {
    this.createOpen.set(false);
    this.editingWalk.set(null);
  }

  protected onWalkModalSaved(): void {
    const city = this.selectedCity();
    this.closeCreateModal();
    if (city) {
      this.reloadWalks(city.id);
    }
  }

  private reloadWalks(cityId: number): void {
    this.loading.set(true);
    this.http.get<Walk[]>('/api/walks', { params: { cityId: cityId.toString() } }).subscribe({
      next: (walks) => {
        this.walks.set(walks);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
