// Suivi GPS live d'une balade démarrée par l'organisateur / encadrant.
import {
  Component,
  ElementRef,
  Injector,
  NgZone,
  OnDestroy,
  OnInit,
  ViewChild,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  GeoJSONSource,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  setWorkerUrl,
} from 'maplibre-gl';
import {
  RouteGeoJson,
  RouteLngLat,
  lineLengthMeters,
} from '../../shared/route-editor/route-editor.component';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../../shared/photo-uploader/photo-uploader.component';

interface LiveWalk {
  id: number;
  title: string;
  cityName?: string | null;
  meetingPointName?: string | null;
  lat?: number | null;
  lng?: number | null;
  startsAt: string;
  durationMinutes: number;
  status: string;
  startedAt?: string | null;
  completedAt?: string | null;
  routeGeojson?: RouteGeoJson | string | null;
  organizerUserId?: number | null;
  galleryCount?: number;
}

@Component({
  selector: 'app-walk-live',
  standalone: true,
  imports: [RouterLink, DatePipe, PhotoUploaderComponent],
  template: `
    <section class="live-page">
      <header class="live-head">
        <a routerLink="/profile" class="back">← Profil</a>
        <div class="titles">
          <p class="eyebrow">Balade en direct</p>
          <h1>{{ walk()?.title || 'Suivi GPS' }}</h1>
          @if (walk(); as w) {
            <p class="meta">
              {{ w.cityName || 'Ville' }}
              @if (w.meetingPointName) {
                · {{ w.meetingPointName }}
              }
            </p>
          }
        </div>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
      @if (geoError()) {
        <p class="banner warn" role="status">{{ geoError() }}</p>
      }
      @if (lastValidatedLabel()) {
        <p class="banner ok" role="status">{{ lastValidatedLabel() }}</p>
      }

      @if (loading()) {
        <p class="muted">Chargement…</p>
      }

      @if (walk(); as w) {
        <div class="stats">
          <div class="stat">
            <span class="label">Statut</span>
            <strong [attr.data-status]="w.status">{{ statusLabel(w.status) }}</strong>
          </div>
          <div class="stat">
            <span class="label">Progression</span>
            <strong>{{ progressPercent() }}%</strong>
          </div>
          <div class="stat">
            <span class="label">Parcouru</span>
            <strong>{{ formatDistance(trailMeters()) }}</strong>
          </div>
          <div class="stat">
            <span class="label">Étapes</span>
            <strong>
              @if (waypointCount() > 0) {
                {{ reachedCount() }}/{{ waypointCount() }}
              } @else {
                —
              }
            </strong>
          </div>
        </div>

        @if (!hasPlannedGeometry()) {
          <p class="banner warn" role="status">
            Aucun parcours enregistré : la carte montre le point de rendez-vous.
          </p>
        }
      }

      <div class="map-panel" [class.is-hidden]="!walk()">
        <div #mapContainer class="map" role="application" aria-label="Carte de suivi GPS"></div>
      </div>

      @if (waypoints().length > 0) {
        <ol class="waypoint-list">
          @for (wp of waypoints(); track wp.index) {
            <li [class.reached]="wp.reached" [class.next]="wp.index === nextWaypointIndex()">
              <span class="wp-num">{{ wp.index + 1 }}</span>
              <span class="wp-label">
                @if (wp.reached) {
                  Point {{ wp.index + 1 }} atteint
                } @else if (wp.index === nextWaypointIndex()) {
                  Prochain point à rejoindre
                } @else {
                  Point {{ wp.index + 1 }}
                }
              </span>
            </li>
          }
        </ol>
      }

      @if (walk(); as w) {
        <div class="actions">
          @if (w.status === 'PUBLISHED') {
            <button type="button" class="btn-primary" (click)="startWalk()" [disabled]="acting()">
              {{ acting() ? 'Démarrage…' : 'Lancer la balade' }}
            </button>
          }
          @if (w.status === 'IN_PROGRESS') {
            <button type="button" class="btn-primary" (click)="completeWalk()" [disabled]="acting()">
              {{ acting() ? 'Clôture…' : 'Terminer la balade' }}
            </button>
          }
          @if (w.status === 'COMPLETED') {
            <p class="done">
              Balade terminée
              @if (w.completedAt) {
                · {{ w.completedAt | date: 'short' }}
              }
            </p>
            <a routerLink="/profile" class="btn-ghost">Retour au profil</a>
          }
        </div>

        @if (canUploadGallery(w)) {
          <section class="gallery-block">
            <app-photo-uploader
              title="Galerie live"
              [uploadUrl]="'/api/walks/' + w.id + '/photos?kind=GALLERY'"
              [deleteUrlBase]="'/api/walks/' + w.id + '/photos'"
              [max]="20"
              [initialPhotos]="galleryPhotos()"
              (photosChange)="galleryPhotos.set($event)"
            />
          </section>
        }
      }
    </section>
  `,
  styles: [
    `
      .live-page {
        max-width: 42rem;
        margin: 0 auto;
        padding: 1rem 1rem 2.5rem;
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
      }
      .gallery-block {
        margin-top: 0.35rem;
        padding-top: 0.75rem;
        border-top: 1px solid var(--line);
      }
      .live-head {
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
      }
      .back {
        color: var(--muted);
        text-decoration: none;
        font-size: 0.85rem;
        font-weight: 600;
        width: fit-content;
      }
      .eyebrow {
        margin: 0;
        font-size: 0.75rem;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--muted);
        font-weight: 700;
      }
      h1 {
        margin: 0.15rem 0 0;
        font-family: var(--font-display);
        font-size: 1.55rem;
        color: var(--forest-deep);
        line-height: 1.15;
      }
      .meta {
        margin: 0.25rem 0 0;
        color: var(--muted);
        font-size: 0.88rem;
      }
      .banner {
        margin: 0;
        padding: 0.65rem 0.8rem;
        border-radius: var(--radius-sm);
        font-size: 0.88rem;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
      }
      .banner.warn {
        background: #fff4df;
        color: #8a5a00;
      }
      .banner.ok {
        background: #e4f5e8;
        color: var(--forest);
        font-weight: 600;
      }
      .muted {
        color: var(--muted);
      }
      .stats {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.55rem;
      }
      @media (min-width: 560px) {
        .stats {
          grid-template-columns: repeat(4, minmax(0, 1fr));
        }
      }
      .stat {
        padding: 0.65rem 0.7rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .label {
        font-size: 0.72rem;
        color: var(--muted);
        font-weight: 600;
      }
      .stat strong {
        font-size: 0.98rem;
        color: var(--forest-deep);
      }
      .stat strong[data-status='IN_PROGRESS'] {
        color: var(--forest);
      }
      .stat strong[data-status='COMPLETED'] {
        color: var(--muted);
      }
      .map-panel {
        position: relative;
        height: min(58dvh, 26rem);
        border-radius: var(--radius-md);
        overflow: hidden;
        border: 1px solid var(--line);
        background: #e4ece3;
        isolation: isolate;
      }
      .map-panel.is-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        opacity: 0;
        pointer-events: none;
        overflow: hidden;
      }
      .map {
        position: absolute;
        inset: 0;
      }
      .waypoint-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .waypoint-list li {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        padding: 0.45rem 0.65rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
        color: var(--muted);
        font-size: 0.86rem;
      }
      .waypoint-list li.next {
        border-color: var(--forest);
        color: var(--forest-deep);
        font-weight: 600;
      }
      .waypoint-list li.reached {
        background: #e8f2e8;
        color: var(--forest);
      }
      .wp-num {
        width: 1.55rem;
        height: 1.55rem;
        border-radius: 50%;
        display: grid;
        place-items: center;
        background: var(--forest-soft);
        color: var(--forest);
        font-size: 0.78rem;
        font-weight: 800;
        flex-shrink: 0;
      }
      .waypoint-list li.next .wp-num {
        background: var(--forest);
        color: var(--on-forest);
      }
      .waypoint-list li.reached .wp-num {
        background: #1f7a3a;
        color: #fff;
      }
      .actions {
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
        align-items: stretch;
      }
      .btn-primary {
        background: var(--forest);
        color: var(--on-forest);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.85rem 1.1rem;
        font-size: 1rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-primary:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-ghost {
        text-align: center;
        text-decoration: none;
        color: var(--forest);
        border: 1px solid var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.7rem 1rem;
        font-weight: 700;
        font-family: var(--font-display);
      }
      .done {
        margin: 0;
        text-align: center;
        color: var(--muted);
        font-size: 0.9rem;
      }
      :host ::ng-deep .bw-live-dot {
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: #1f7a3a;
        border: 3px solid #fff;
        box-shadow: 0 0 0 6px rgba(31, 122, 58, 0.22);
      }
      :host ::ng-deep .bw-rdv-dot {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: var(--forest-deep);
        border: 2px solid #fff;
      }
      :host ::ng-deep .bw-wp {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        display: grid;
        place-items: center;
        font-size: 0.75rem;
        font-weight: 800;
        font-family: var(--font-display), system-ui, sans-serif;
        color: #fff;
        background: #5b7a5d;
        border: 2px solid #fff;
        box-shadow: 0 1px 5px rgba(31, 61, 34, 0.35);
        transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
      }
      :host ::ng-deep .bw-wp.is-next {
        background: #c45c26;
        box-shadow: 0 0 0 5px rgba(196, 92, 38, 0.28);
        transform: scale(1.08);
      }
      :host ::ng-deep .bw-wp.is-reached {
        background: #1f9d4a;
        box-shadow: 0 0 0 4px rgba(31, 157, 74, 0.28);
      }
      :host ::ng-deep .bw-wp.is-start:not(.is-reached):not(.is-next) {
        border-radius: 7px;
      }
      :host ::ng-deep .maplibregl-ctrl-attrib {
        font-size: 9px;
      }
    `,
  ],
})
export class WalkLiveComponent implements OnInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly zone = inject(NgZone);

  protected readonly walk = signal<LiveWalk | null>(null);
  protected readonly loading = signal(true);
  protected readonly acting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly geoError = signal<string | null>(null);
  protected readonly lastValidatedLabel = signal<string | null>(null);
  protected readonly progressPercent = signal(0);
  protected readonly trailMeters = signal(0);
  protected readonly hasPlannedGeometry = signal(false);
  protected readonly waypoints = signal<WaypointView[]>([]);
  protected readonly nextWaypointIndex = signal(0);
  protected readonly reachedCount = signal(0);
  protected readonly waypointCount = signal(0);
  protected readonly galleryPhotos = signal<PhotoItem[]>([]);

  private map?: MapLibreMap;
  private mapReady = false;
  private gpsMarker?: Marker;
  private rdvMarker?: Marker;
  private waypointMarkers: Marker[] = [];
  private watchId: number | null = null;
  private trail: RouteLngLat[] = [];
  private plannedRoute: RouteLngLat[] = [];
  private reachedFlags: boolean[] = [];
  private resizeObserver?: ResizeObserver;
  private validatedBannerTimer?: ReturnType<typeof setTimeout>;

  /** Rayon (m) pour valider un point au passage. */
  private static readonly WAYPOINT_RADIUS_M = 60;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id)) {
      this.error.set('Balade introuvable.');
      this.loading.set(false);
      return;
    }
    this.loadWalk(id);
  }

  ngOnDestroy(): void {
    this.stopGps();
    if (this.validatedBannerTimer) {
      clearTimeout(this.validatedBannerTimer);
    }
    this.resizeObserver?.disconnect();
    this.gpsMarker?.remove();
    this.rdvMarker?.remove();
    this.clearWaypointMarkers();
    this.map?.remove();
  }

  protected statusLabel(status: string): string {
    switch (status) {
      case 'PUBLISHED':
        return 'Prête';
      case 'IN_PROGRESS':
        return 'En cours';
      case 'COMPLETED':
        return 'Terminée';
      case 'CANCELLED':
        return 'Annulée';
      default:
        return status;
    }
  }

  protected formatDistance(meters: number): string {
    if (meters < 1000) {
      return `${Math.round(meters)} m`;
    }
    return `${(meters / 1000).toFixed(meters >= 10_000 ? 0 : 1)} km`;
  }

  protected canUploadGallery(walk: LiveWalk): boolean {
    return walk.status === 'IN_PROGRESS' || walk.status === 'COMPLETED';
  }

  protected startWalk(): void {
    const current = this.walk();
    if (!current || this.acting()) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.http.post<LiveWalk>(`/api/walks/${current.id}/start`, {}).subscribe({
      next: (updated) => {
        this.acting.set(false);
        this.applyWalk(updated);
        this.startGps();
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.error.set(this.apiError(err, 'Impossible de démarrer la balade.'));
      },
    });
  }

  protected completeWalk(): void {
    const current = this.walk();
    if (!current || this.acting()) {
      return;
    }
    this.acting.set(true);
    this.error.set(null);
    this.http.post<LiveWalk>(`/api/walks/${current.id}/complete`, {}).subscribe({
      next: (updated) => {
        this.acting.set(false);
        this.stopGps();
        this.applyWalk(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.acting.set(false);
        this.error.set(this.apiError(err, 'Impossible de terminer la balade.'));
      },
    });
  }

  private loadWalk(id: number): void {
    this.loading.set(true);
    this.galleryPhotos.set([]);
    this.http.get<LiveWalk>(`/api/walks/${id}`).subscribe({
      next: (walk) => {
        this.loading.set(false);
        this.applyWalk(walk);
        this.loadGallery(id);
        afterNextRender(
          () => {
            this.tryInitMap();
            // Conteneur était masqué : forcer un resize une fois visible.
            requestAnimationFrame(() => this.map?.resize());
          },
          { injector: this.injector },
        );
        if (walk.status === 'IN_PROGRESS') {
          this.startGps();
        } else if (walk.status === 'PUBLISHED') {
          this.startWalk();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.apiError(err, 'Impossible de charger la balade.'));
      },
    });
  }

  private loadGallery(walkId: number): void {
    this.http
      .get<Array<{ id: number; url: string; sortOrder: number }>>(`/api/walks/${walkId}/photos`, {
        params: { kind: 'GALLERY' },
      })
      .subscribe({
        next: (photos) =>
          this.galleryPhotos.set(
            photos.map((p) => ({ id: p.id, url: p.url, sortOrder: p.sortOrder })),
          ),
        error: () => this.galleryPhotos.set([]),
      });
  }

  private applyWalk(walk: LiveWalk): void {
    this.walk.set(walk);
    this.plannedRoute = parseCoordinates(walk.routeGeojson);
    this.hasPlannedGeometry.set(this.plannedRoute.length >= 2);
    this.resetWaypoints(this.plannedRoute);
    if (this.mapReady) {
      this.renderPlannedRoute();
      this.renderWaypoints();
      this.renderMeetingPoint();
      this.fitUsefulBounds();
      this.map?.resize();
    }
  }

  private resetWaypoints(route: RouteLngLat[]): void {
    this.reachedFlags = route.map(() => false);
    this.waypointCount.set(route.length);
    this.reachedCount.set(0);
    this.nextWaypointIndex.set(route.length > 0 ? 0 : -1);
    this.syncWaypointViews();
  }

  private syncWaypointViews(): void {
    const next = this.reachedFlags.findIndex((reached) => !reached);
    this.nextWaypointIndex.set(next);
    this.waypoints.set(
      this.plannedRoute.map((_, index) => ({
        index,
        reached: !!this.reachedFlags[index],
      })),
    );
    const reached = this.reachedFlags.filter(Boolean).length;
    this.reachedCount.set(reached);
    if (this.plannedRoute.length > 0) {
      this.progressPercent.set(Math.round((reached / this.plannedRoute.length) * 100));
    }
    this.applyWaypointMarkerStyles();
  }

  private applyWaypointMarkerStyles(): void {
    if (!this.mapReady || this.waypointMarkers.length !== this.plannedRoute.length) {
      return;
    }
    const next = this.nextWaypointIndex();
    this.waypointMarkers.forEach((marker, index) => {
      const el = marker.getElement();
      const reached = !!this.reachedFlags[index];
      const isNext = index === next && !reached;
      el.classList.toggle('is-reached', reached);
      el.classList.toggle('is-next', isNext);
      // Couleurs inline pour garantir le refresh hors zone Angular / MapLibre.
      if (reached) {
        el.style.background = '#1f9d4a';
        el.textContent = '✓';
      } else if (isNext) {
        el.style.background = '#c45c26';
        el.textContent = String(index + 1);
      } else {
        el.style.background = '#5b7a5d';
        el.textContent = String(index + 1);
      }
    });
  }

  private tryInitMap(): void {
    if (this.map || !this.mapContainer?.nativeElement || !this.walk()) {
      return;
    }
    setWorkerUrl(new URL('maplibre-gl-worker.mjs', document.baseURI).href);
    const center = this.resolveCenter();
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
      center: [center.lng, center.lat],
      zoom: 14,
      minZoom: 4.2,
      maxZoom: 19,
      maxBounds: [
        [-6.5, 40.8],
        [10.5, 51.8],
      ],
    });
    this.map = map;
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    map.once('load', () => {
      this.mapReady = true;
      map.addSource('planned-route', {
        type: 'geojson',
        data: emptyLine(),
      });
      map.addLayer({
        id: 'planned-route-layer',
        type: 'line',
        source: 'planned-route',
        paint: {
          'line-color': '#2f5d32',
          'line-width': 4,
          'line-opacity': 0.75,
        },
      });
      map.addSource('gps-trail', {
        type: 'geojson',
        data: emptyLine(),
      });
      map.addLayer({
        id: 'gps-trail-layer',
        type: 'line',
        source: 'gps-trail',
        paint: {
          'line-color': '#1f7a3a',
          'line-width': 3,
          'line-opacity': 0.95,
        },
      });
      this.renderPlannedRoute();
      this.renderWaypoints();
      this.renderMeetingPoint();
      this.fitUsefulBounds();
      map.resize();
    });

    this.resizeObserver = new ResizeObserver(() => this.map?.resize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  private startGps(): void {
    if (this.watchId != null) {
      return;
    }
    if (!('geolocation' in navigator)) {
      this.geoError.set(
        'Géolocalisation indisponible sur cet appareil — le parcours reste visible sur la carte.',
      );
      return;
    }
    this.geoError.set(null);
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => this.zone.run(() => this.onGps(pos)),
      (err) =>
        this.zone.run(() => {
          if (err.code === err.PERMISSION_DENIED) {
            this.geoError.set(
              'Autorisez la localisation (icône cadenas dans la barre d’adresse) pour valider les points. Sur PC, cliquez un point sur la carte pour le tester.',
            );
          } else if (err.code === err.POSITION_UNAVAILABLE) {
            this.geoError.set(
              'Position GPS indisponible — cliquez un point sur la carte pour le valider (test PC).',
            );
          } else {
            this.geoError.set(
              'Position GPS introuvable — cliquez un point sur la carte pour le valider (test PC).',
            );
          }
        }),
      {
        enableHighAccuracy: true,
        maximumAge: 1_000,
        timeout: 15_000,
      },
    );
  }

  private stopGps(): void {
    if (this.watchId != null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
  }

  private onGps(pos: GeolocationPosition): void {
    const lng = pos.coords.longitude;
    const lat = pos.coords.latitude;
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
      return;
    }
    const point: RouteLngLat = [lng, lat];
    const last = this.trail[this.trail.length - 1];
    if (!last || haversineMeters(last, point) >= 4) {
      this.trail.push(point);
    }
    this.trailMeters.set(lineLengthMeters(this.trail));
    this.updateWaypointProgress(point);
    this.geoError.set(null);

    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    if (!this.gpsMarker) {
      const el = document.createElement('div');
      el.className = 'bw-live-dot';
      this.gpsMarker = new Marker({ element: el }).setLngLat([lng, lat]).addTo(map);
    } else {
      this.gpsMarker.setLngLat([lng, lat]);
    }

    const trailSource = map.getSource('gps-trail') as GeoJSONSource | undefined;
    trailSource?.setData(
      this.trail.length >= 2
        ? {
            type: 'Feature',
            properties: {},
            geometry: { type: 'LineString', coordinates: this.trail },
          }
        : emptyLine(),
    );

    map.easeTo({ center: [lng, lat], duration: 600 });
  }

  private updateWaypointProgress(point: RouteLngLat): void {
    if (this.plannedRoute.length === 0) {
      this.progressPercent.set(0);
      return;
    }
    const newlyReached: number[] = [];
    // Tout point à proximité est validé (pas besoin d'ordre strict).
    for (let i = 0; i < this.plannedRoute.length; i++) {
      if (this.reachedFlags[i]) {
        continue;
      }
      if (haversineMeters(point, this.plannedRoute[i]) <= WalkLiveComponent.WAYPOINT_RADIUS_M) {
        this.reachedFlags[i] = true;
        newlyReached.push(i);
      }
    }
    if (newlyReached.length > 0) {
      const labels = newlyReached.map((i) => i + 1).join(', ');
      this.flashValidated(
        newlyReached.length === 1
          ? `Point ${labels} validé`
          : `Points ${labels} validés`,
      );
      this.syncWaypointViews();
      return;
    }
    this.applyWaypointMarkerStyles();
    const reached = this.reachedFlags.filter(Boolean).length;
    this.progressPercent.set(
      this.reachedFlags.every(Boolean)
        ? 100
        : Math.min(99, Math.round((reached / this.plannedRoute.length) * 100)),
    );
  }

  private flashValidated(message: string): void {
    this.lastValidatedLabel.set(message);
    if (this.validatedBannerTimer) {
      clearTimeout(this.validatedBannerTimer);
    }
    this.validatedBannerTimer = setTimeout(() => this.lastValidatedLabel.set(null), 3500);
  }

  /** Test PC : clic sur un marqueur = validation manuelle du point. */
  private validateWaypointByIndex(index: number): void {
    if (index < 0 || index >= this.plannedRoute.length || this.reachedFlags[index]) {
      return;
    }
    this.reachedFlags[index] = true;
    this.flashValidated(`Point ${index + 1} validé`);
    this.syncWaypointViews();
  }

  private renderPlannedRoute(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    const source = map.getSource('planned-route') as GeoJSONSource | undefined;
    source?.setData(
      this.plannedRoute.length >= 2
        ? {
            type: 'Feature',
            properties: {},
            geometry: { type: 'LineString', coordinates: this.plannedRoute },
          }
        : emptyLine(),
    );
  }

  private renderWaypoints(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    this.clearWaypointMarkers();
    this.plannedRoute.forEach((coord, index) => {
      const el = document.createElement('div');
      el.className = 'bw-wp';
      if (index === 0) {
        el.classList.add('is-start');
      }
      el.textContent = String(index + 1);
      el.title = `Point ${index + 1} — passez à proximité ou cliquez pour valider`;
      el.style.cursor = 'pointer';
      el.addEventListener('click', (event) => {
        event.stopPropagation();
        this.zone.run(() => this.validateWaypointByIndex(index));
      });
      this.waypointMarkers.push(new Marker({ element: el }).setLngLat(coord).addTo(map));
    });
    this.applyWaypointMarkerStyles();
  }

  private clearWaypointMarkers(): void {
    for (const marker of this.waypointMarkers) {
      marker.remove();
    }
    this.waypointMarkers = [];
  }

  private renderMeetingPoint(): void {
    const map = this.map;
    const walk = this.walk();
    if (!map || !this.mapReady || !walk) {
      return;
    }
    this.rdvMarker?.remove();
    this.rdvMarker = undefined;
    // Si un parcours existe, les points numérotés suffisent (évite un doublon RDV).
    if (this.plannedRoute.length >= 2) {
      return;
    }
    if (walk.lat == null || walk.lng == null) {
      return;
    }
    const el = document.createElement('div');
    el.className = 'bw-rdv-dot';
    this.rdvMarker = new Marker({ element: el }).setLngLat([walk.lng, walk.lat]).addTo(map);
  }

  private fitUsefulBounds(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    const bounds = new LngLatBounds();
    let has = false;
    for (const [lng, lat] of this.plannedRoute) {
      bounds.extend([lng, lat]);
      has = true;
    }
    const walk = this.walk();
    if (walk?.lng != null && walk.lat != null) {
      bounds.extend([walk.lng, walk.lat]);
      has = true;
    }
    if (has) {
      map.fitBounds(bounds, { padding: 48, maxZoom: 15, duration: 450 });
    } else {
      const c = this.resolveCenter();
      map.flyTo({ center: [c.lng, c.lat], zoom: 14, duration: 450 });
    }
  }

  private resolveCenter(): { lat: number; lng: number } {
    const walk = this.walk();
    if (walk?.lat != null && walk.lng != null) {
      return { lat: walk.lat, lng: walk.lng };
    }
    if (this.plannedRoute.length > 0) {
      const [lng, lat] = this.plannedRoute[0];
      return { lat, lng };
    }
    return { lat: 46.6, lng: 2.5 };
  }

  private apiError(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 401) {
      this.router.navigate(['/login']);
      return 'Connectez-vous pour lancer la balade.';
    }
    if (err.status === 403) {
      return 'Seul l’organisateur ou l’encadrant peut lancer cette balade.';
    }
    const body = err.error;
    if (body && typeof body === 'object') {
      const detail =
        (body as { detail?: string; title?: string; message?: string }).detail
        ?? (body as { title?: string }).title
        ?? (body as { message?: string }).message;
      if (detail) {
        return String(detail);
      }
    }
    return fallback;
  }
}

interface WaypointView {
  index: number;
  reached: boolean;
}

function parseCoordinates(raw: LiveWalk['routeGeojson']): RouteLngLat[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed = typeof raw === 'string' ? (JSON.parse(raw) as RouteGeoJson) : raw;
    if (parsed?.type === 'LineString' && Array.isArray(parsed.coordinates)) {
      return parsed.coordinates.filter(
        (c): c is RouteLngLat =>
          Array.isArray(c) && c.length >= 2 && Number.isFinite(c[0]) && Number.isFinite(c[1]),
      );
    }
  } catch {
    return [];
  }
  return [];
}

function emptyLine(): {
  type: 'Feature';
  properties: Record<string, never>;
  geometry: { type: 'LineString'; coordinates: RouteLngLat[] };
} {
  return {
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates: [] },
  };
}

function haversineMeters(a: RouteLngLat, b: RouteLngLat): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b[1] - a[1]);
  const dLng = toRad(b[0] - a[0]);
  const lat1 = toRad(a[1]);
  const lat2 = toRad(b[1]);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}
