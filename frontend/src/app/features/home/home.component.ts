// Accueil POC : marque, salutation, prochaine balade, liste de la semaine + carte France.
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import {
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  Popup,
} from 'maplibre-gl';
import { AuthService } from '../../core/auth.service';

export interface WalkPin {
  id: number;
  title: string;
  description: string;
  walkType: string;
  cityId: number;
  cityName: string | null;
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

interface UserProfileLite {
  firstName: string;
  lastName: string;
  roles: string[];
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="home">
      <header class="top-row">
        <a routerLink="/" class="brand" aria-label="Bark & Walk — accueil">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <circle cx="7.2" cy="8" r="1.5" fill="currentColor" />
              <circle cx="16.8" cy="8" r="1.5" fill="currentColor" />
              <circle cx="5" cy="12.2" r="1.35" fill="currentColor" />
              <circle cx="19" cy="12.2" r="1.35" fill="currentColor" />
              <ellipse cx="12" cy="15.4" rx="3" ry="2.4" fill="currentColor" />
            </svg>
          </span>
          <span class="brand-wordmark">BARK &amp; WALK</span>
        </a>
        <button type="button" class="icon-btn" aria-label="Notifications">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M6 9.5a6 6 0 0 1 12 0v3.2l1.4 2.1H4.6L6 12.7V9.5Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
            <path
              d="M10 18.2a2 2 0 0 0 4 0"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </header>

      <section class="greeting" aria-labelledby="home-hello">
        <h1 id="home-hello">Bonjour {{ firstName() }}</h1>
        <div class="greeting-meta">
          <span class="bw-pill bw-pill-member">{{ membershipLabel() }}</span>
          @if (cityHint()) {
            <span class="dogs-hint">{{ cityHint() }}</span>
          }
        </div>
      </section>

      <label class="bw-search" aria-label="Rechercher une balade">
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.8" />
          <path d="m16 16 3.5 3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        <input
          type="search"
          placeholder="Une balade, un quartier, un encadrant…"
          [value]="query()"
          (input)="onQuery($event)"
        />
      </label>

      @if (nextWalk(); as walk) {
        <a class="bw-card-hero next-walk" [routerLink]="['/booking']" [queryParams]="{ walkId: walk.id }">
          <div class="next-top">
            <p class="next-when">{{ walk.startsAt | date: "EEEE d MMM · HH:mm" }}</p>
            <span class="bw-pill bw-pill-urgent">{{ daysUntilLabel(walk.startsAt) }}</span>
          </div>
          <h2>{{ walk.title }}</h2>
          <p class="next-place">
            <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
              <path
                d="M12 21s6-5.4 6-10a6 6 0 1 0-12 0c0 4.6 6 10 6 10Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              />
              <circle cx="12" cy="11" r="2" fill="currentColor" />
            </svg>
            {{ walk.meetingPointName || walk.meetingPointAddress || walk.cityName || 'Lieu à préciser' }}
          </p>
          <p class="next-extra">{{ formatDuration(walk.durationMinutes) }} · {{ seatsLeft(walk) }}</p>
        </a>
      }

      <section class="week" aria-labelledby="week-title">
        <div class="week-head">
          <h2 id="week-title">Cette semaine{{ weekCity() ? ' à ' + weekCity() : '' }}</h2>
          <a routerLink="/catalog" class="see-all">Voir tout</a>
        </div>

        @if (loading()) {
          <p class="state muted">Chargement des balades…</p>
        } @else if (error()) {
          <p class="state error">{{ error() }}</p>
        } @else if (weekWalks().length === 0) {
          <p class="state muted">Aucune balade publiée pour le moment.</p>
        } @else {
          <ul class="week-list">
            @for (walk of weekWalks(); track walk.id) {
              <li>
                <a class="bw-card walk-row" [routerLink]="['/booking']" [queryParams]="{ walkId: walk.id }">
                  <div class="row-badges">
                    <span class="bw-pill" [class]="typePillClass(walk.walkType)">{{ typeLabel(walk.walkType) }}</span>
                    <span class="bw-pill bw-pill-soft">{{ energyHint(walk.durationMinutes) }}</span>
                  </div>
                  <h3>{{ walk.title }}</h3>
                  <p class="row-meta">
                    {{ walk.startsAt | date: "EEE d MMM · HH:mm" }}
                    · {{ formatDuration(walk.durationMinutes) }}
                    @if (walk.cityName) {
                      · {{ walk.cityName }}
                    }
                  </p>
                  <div class="row-foot">
                    <span class="price-hint">{{ priceLabel(walk) }}</span>
                    <span class="seats" [class.tight]="remaining(walk) <= 3">{{ seatsLeft(walk) }}</span>
                  </div>
                </a>
              </li>
            }
          </ul>
        }
      </section>

      <section class="map-block" aria-label="Carte des balades en France">
        <div class="map-head">
          <h2>Sur la carte</h2>
          <p class="muted">Pins des balades publiées</p>
        </div>
        <div class="map-frame">
          <div #mapContainer class="map" role="presentation"></div>
          @if (loading()) {
            <p class="map-status">Chargement…</p>
          } @else if (pinCount() === 0 && !error()) {
            <p class="map-status">Aucun pin géolocalisé</p>
          }
        </div>
      </section>
    </div>
  `,
  styles: [
    `
      .home {
        width: min(100%, 28rem);
        margin: 0 auto;
        padding: 0.85rem 1rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }

      .top-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        animation: rise 0.45s ease both;
      }

      .brand {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        text-decoration: none;
        color: var(--forest);
      }

      .brand-mark {
        width: 1.55rem;
        height: 1.55rem;
        display: grid;
        place-items: center;
        color: var(--forest);
      }

      .brand-mark svg {
        width: 100%;
        height: 100%;
      }

      .brand-wordmark {
        font-size: 1.05rem;
        letter-spacing: 0.04em;
        color: var(--forest);
      }

      .icon-btn {
        width: 2.5rem;
        height: 2.5rem;
        border-radius: 50%;
        border: 1px solid var(--line);
        background: var(--surface);
        color: var(--forest-deep);
        display: grid;
        place-items: center;
        cursor: pointer;
        box-shadow: var(--shadow-sm);
      }

      .icon-btn svg {
        width: 1.15rem;
        height: 1.15rem;
      }

      .greeting {
        animation: rise 0.5s ease 0.05s both;
      }

      .greeting h1 {
        margin: 0 0 0.45rem;
        font-size: clamp(1.65rem, 7vw, 2rem);
        color: var(--ink);
      }

      .greeting-meta {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.55rem;
      }

      .dogs-hint {
        color: var(--muted);
        font-size: 0.88rem;
        font-weight: 500;
      }

      .bw-search {
        animation: rise 0.5s ease 0.08s both;
      }

      .next-walk {
        display: block;
        text-decoration: none;
        animation: rise 0.55s ease 0.1s both;
        transition: transform 0.2s ease;
      }

      .next-walk:hover {
        transform: translateY(-2px);
      }

      .next-top {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 0.75rem;
        margin-bottom: 0.55rem;
      }

      .next-when {
        margin: 0;
        font-size: 0.88rem;
        opacity: 0.9;
        text-transform: capitalize;
      }

      .next-walk h2 {
        margin: 0 0 0.55rem;
        font-size: 1.35rem;
        line-height: 1.2;
        color: var(--on-forest);
      }

      .next-place {
        margin: 0 0 0.35rem;
        display: flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.88rem;
        opacity: 0.92;
      }

      .next-extra {
        margin: 0;
        font-size: 0.82rem;
        opacity: 0.8;
      }

      .week-head {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.75rem;
        margin-bottom: 0.75rem;
      }

      .week-head h2 {
        margin: 0;
        font-size: 1.1rem;
      }

      .see-all {
        color: var(--forest);
        font-weight: 700;
        font-size: 0.88rem;
      }

      .week-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }

      .walk-row {
        display: block;
        text-decoration: none;
        transition: transform 0.18s ease;
      }

      .walk-row:hover {
        transform: translateY(-1px);
      }

      .row-badges {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
        margin-bottom: 0.45rem;
      }

      .walk-row h3 {
        margin: 0 0 0.35rem;
        font-size: 1.02rem;
        line-height: 1.25;
        color: var(--forest-deep);
      }

      .row-meta {
        margin: 0 0 0.7rem;
        color: var(--muted);
        font-size: 0.84rem;
        text-transform: capitalize;
      }

      .row-foot {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        font-size: 0.82rem;
        font-weight: 600;
      }

      .price-hint {
        color: var(--forest);
      }

      .seats {
        color: var(--muted);
      }

      .seats.tight {
        color: var(--warn);
      }

      .state {
        margin: 0;
        padding: 0.75rem 0.25rem;
        font-size: 0.92rem;
      }

      .state.error {
        color: var(--danger);
      }

      .map-block {
        margin-top: 0.25rem;
        animation: rise 0.55s ease 0.12s both;
      }

      .map-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.1rem;
      }

      .map-head .muted {
        margin: 0 0 0.65rem;
        font-size: 0.85rem;
      }

      .map-frame {
        position: relative;
        height: min(36dvh, 14rem);
        border-radius: var(--radius-md);
        overflow: hidden;
        border: 1px solid var(--line);
        background: #e4ece3;
        isolation: isolate;
      }

      .map {
        position: absolute;
        inset: 0;
      }

      .map-status {
        position: absolute;
        z-index: 2;
        left: 50%;
        bottom: 0.65rem;
        transform: translateX(-50%);
        margin: 0;
        padding: 0.4rem 0.75rem;
        border-radius: var(--radius-pill);
        background: rgba(31, 61, 34, 0.88);
        color: #f7f5ef;
        font-size: 0.78rem;
        white-space: nowrap;
      }

      :host ::ng-deep .bw-marker {
        width: 22px;
        height: 22px;
        border-radius: 50% 50% 50% 0;
        background: var(--forest);
        border: 2px solid #fff;
        box-shadow: 0 2px 8px rgba(31, 61, 34, 0.35);
        transform: rotate(-45deg);
        cursor: pointer;
      }

      :host ::ng-deep .bw-marker.is-active {
        background: var(--cta);
        transform: rotate(-45deg) scale(1.15);
      }

      :host ::ng-deep .maplibregl-popup-content {
        font-family: var(--font-body);
        padding: 0.5rem 0.65rem;
        border-radius: 10px;
        font-size: 0.82rem;
      }

      :host ::ng-deep .maplibregl-ctrl-attrib {
        font-size: 9px;
      }

      @keyframes rise {
        from {
          opacity: 0;
          transform: translateY(10px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      @media (min-width: 640px) {
        .home {
          width: min(100%, 32rem);
          padding-top: 1.1rem;
        }
        .map-frame {
          height: min(40dvh, 18rem);
        }
      }
    `,
  ],
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private map?: MapLibreMap;
  private markers: Marker[] = [];
  private readonly markerEls = new globalThis.Map<number, HTMLElement>();
  private pendingWalks: WalkPin[] | null = null;
  private mapReady = false;
  private walksRequestStarted = false;
  private resizeObserver?: ResizeObserver;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pinCount = signal(0);
  protected readonly walks = signal<WalkPin[]>([]);
  protected readonly query = signal('');
  protected readonly firstName = signal('là');
  protected readonly membershipLabel = signal('Invité · découverte');
  protected readonly cityHint = signal<string | null>(null);

  protected readonly filteredWalks = computed(() => {
    const q = this.query().trim().toLowerCase();
    const list = [...this.walks()].sort(
      (a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime(),
    );
    if (!q) {
      return list;
    }
    return list.filter((w) =>
      [w.title, w.cityName, w.meetingPointName, w.description]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q)),
    );
  });

  protected readonly nextWalk = computed(() => this.filteredWalks()[0] ?? null);

  protected readonly weekWalks = computed(() => {
    const all = this.filteredWalks();
    return all.slice(this.nextWalk() ? 1 : 0, 6);
  });

  protected readonly weekCity = computed(() => {
    const cities = this.filteredWalks()
      .map((w) => w.cityName)
      .filter((c): c is string => !!c);
    if (cities.length === 0) {
      return null;
    }
    const top = cities[0];
    return cities.every((c) => c === top) ? top : null;
  });

  ngOnInit(): void {
    if (this.auth.loggedIn()) {
      this.http.get<UserProfileLite>('/api/me/profile').subscribe({
        next: (p) => {
          this.firstName.set(p.firstName || 'là');
          const isMember = (p.roles ?? []).some((r) =>
            ['ROLE_MEMBER', 'MEMBER', 'ROLE_USER', 'USER'].includes(r),
          );
          this.membershipLabel.set(isMember ? 'Adhérente · Club' : 'Compte · Bark & Walk');
        },
        error: () => {
          this.firstName.set('là');
        },
      });
    }
  }

  ngAfterViewInit(): void {
    const map = new MapLibreMap({
      container: this.mapContainer.nativeElement,
      style: {
        version: 8,
        sources: {
          osmfr: {
            type: 'raster',
            tiles: [
              'https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',
              'https://b.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',
              'https://c.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',
            ],
            tileSize: 256,
            attribution: '© OpenStreetMap France',
          },
        },
        layers: [{ id: 'osmfr', type: 'raster', source: 'osmfr' }],
      },
      center: [2.5, 46.6],
      zoom: 5.1,
      minZoom: 4.2,
      maxZoom: 14,
      maxBounds: [
        [-6.5, 40.8],
        [10.5, 51.8],
      ],
    });
    this.map = map;
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    map.once('load', () => {
      this.mapReady = true;
      map.resize();
      this.renderPendingMarkers();
    });
    this.loadWalks();
    this.resizeObserver = new ResizeObserver(() => this.map?.resize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.clearMarkers();
    this.map?.remove();
  }

  protected onQuery(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
  }

  protected daysUntilLabel(startsAt: string): string {
    const start = new Date(startsAt);
    const now = new Date();
    const dayMs = 24 * 60 * 60 * 1000;
    const diff = Math.ceil((start.setHours(0, 0, 0, 0) - now.setHours(0, 0, 0, 0)) / dayMs);
    if (diff <= 0) {
      return "Aujourd'hui";
    }
    if (diff === 1) {
      return 'J-1';
    }
    return `J-${diff}`;
  }

  protected formatDuration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) {
      return `${m} min`;
    }
    if (m === 0) {
      return `${h} h`;
    }
    return `${h} h ${String(m).padStart(2, '0')}`;
  }

  protected remaining(walk: WalkPin): number {
    return Math.max(0, walk.capacity - walk.bookedCount);
  }

  protected seatsLeft(walk: WalkPin): string {
    const left = this.remaining(walk);
    if (left <= 0) {
      return 'Complet';
    }
    if (left <= 3) {
      return `${left} place${left > 1 ? 's' : ''} restante${left > 1 ? 's' : ''}`;
    }
    return `${walk.bookedCount} / ${walk.capacity} chiens`;
  }

  protected typeLabel(walkType: string): string {
    const t = (walkType || '').toUpperCase();
    if (t.includes('COMMUN') || t.includes('OPEN')) {
      return 'Communautaire · non encadrée';
    }
    if (t.includes('GUID') || t.includes('PRO') || t.includes('ENCADR')) {
      return 'Encadrée';
    }
    return walkType || 'Balade';
  }

  protected typePillClass(walkType: string): string {
    const t = (walkType || '').toUpperCase();
    if (t.includes('COMMUN') || t.includes('OPEN')) {
      return 'bw-pill bw-pill-community';
    }
    return 'bw-pill bw-pill-guided';
  }

  protected energyHint(minutes: number): string {
    if (minutes >= 90) {
      return 'Énergie +++';
    }
    if (minutes >= 60) {
      return 'Énergie ++';
    }
    return 'Énergie +';
  }

  protected priceLabel(walk: WalkPin): string {
    if (walk.priceCents <= 0) {
      return 'Gratuite · adhérents';
    }
    return 'Incluse dans l’adhésion';
  }

  private loadWalks(): void {
    if (this.walksRequestStarted) {
      return;
    }
    this.walksRequestStarted = true;
    this.http.get<WalkPin[]>('/api/walks').subscribe({
      next: (walks) => {
        this.walks.set(walks);
        const withCoords = walks.filter(
          (w) => w.lat != null && w.lng != null && Number.isFinite(w.lat) && Number.isFinite(w.lng),
        );
        this.pendingWalks = withCoords;
        this.pinCount.set(withCoords.length);
        this.loading.set(false);
        const city = walks.find((w) => w.cityName)?.cityName;
        if (city) {
          this.cityHint.set(city);
        }
        this.renderPendingMarkers();
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les balades.');
      },
    });
  }

  private renderPendingMarkers(): void {
    if (!this.mapReady || !this.pendingWalks) {
      return;
    }
    this.placeMarkers(this.pendingWalks);
  }

  private placeMarkers(walks: WalkPin[]): void {
    const map = this.map;
    if (!map) {
      return;
    }
    this.clearMarkers();

    for (const walk of walks) {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = 'bw-marker';
      el.setAttribute('aria-label', `${walk.title} — ${walk.cityName ?? ''}`);
      el.addEventListener('click', (event) => {
        event.stopPropagation();
        this.highlightMarker(walk.id);
        this.map?.flyTo({
          center: [walk.lng!, walk.lat!],
          zoom: Math.max(this.map.getZoom(), 9),
          duration: 700,
        });
      });

      const popup = new Popup({ offset: 18, closeButton: false }).setHTML(
        `<strong>${escapeHtml(walk.title)}</strong><br/><span>${escapeHtml(walk.cityName ?? '')}</span>`,
      );

      const marker = new Marker({ element: el, anchor: 'bottom' })
        .setLngLat([walk.lng!, walk.lat!])
        .setPopup(popup)
        .addTo(map);

      this.markers.push(marker);
      this.markerEls.set(walk.id, el);
    }

    if (walks.length >= 2) {
      const bounds = new LngLatBounds();
      for (const walk of walks) {
        bounds.extend([walk.lng!, walk.lat!]);
      }
      map.fitBounds(bounds, { padding: 36, maxZoom: 5.8, duration: 900 });
    } else if (walks.length === 1) {
      map.flyTo({ center: [walks[0].lng!, walks[0].lat!], zoom: 10, duration: 900 });
    }
  }

  private highlightMarker(walkId: number): void {
    for (const [id, el] of this.markerEls) {
      el.classList.toggle('is-active', id === walkId);
    }
  }

  private clearMarkers(): void {
    for (const marker of this.markers) {
      marker.remove();
    }
    this.markers = [];
    this.markerEls.clear();
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
