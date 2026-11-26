// Éditeur de parcours MapLibre : clics = waypoints reliés en LineString.
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  signal,
} from '@angular/core';
import {
  GeoJSONSource,
  LngLatBounds,
  Map as MapLibreMap,
  MapMouseEvent,
  Marker,
  NavigationControl,
  setWorkerUrl,
} from 'maplibre-gl';

export type RouteLngLat = [number, number];

export interface RouteGeoJson {
  type: 'LineString';
  coordinates: RouteLngLat[];
}

@Component({
  selector: 'app-route-editor',
  standalone: true,
  template: `
    <div class="route-editor">
      <div class="toolbar">
        <p class="hint">Cliquez sur la carte pour ajouter des points (min. 2).</p>
        <div class="actions">
          <button type="button" class="btn-ghost" (click)="undo()" [disabled]="coordinates().length === 0">
            Annuler
          </button>
          <button type="button" class="btn-ghost" (click)="clear()" [disabled]="coordinates().length === 0">
            Effacer
          </button>
        </div>
      </div>
      <div class="map-wrap">
        <div #mapContainer class="map" role="application" aria-label="Éditeur de parcours"></div>
      </div>
      <p class="count">
        {{ coordinates().length }} point{{ coordinates().length === 1 ? '' : 's' }}
        @if (distanceMeters() > 0) {
          <span class="stats">
            · {{ formatKm(distanceMeters()) }}
            · ~{{ estimatedMinutes() }} min estimées
          </span>
        }
      </p>
    </div>
  `,
  styles: [
    `
      .route-editor {
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
      }
      .toolbar {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .hint {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 500;
        color: var(--muted);
      }
      .actions {
        display: flex;
        gap: 0.4rem;
      }
      .btn-ghost {
        background: transparent;
        border: 1px solid var(--line);
        border-radius: var(--radius-pill);
        padding: 0.35rem 0.75rem;
        font-size: 0.8rem;
        font-weight: 600;
        cursor: pointer;
        color: var(--muted);
      }
      .btn-ghost:disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }
      .map-wrap {
        position: relative;
        height: 12.5rem;
        border-radius: var(--radius-md);
        overflow: hidden;
        border: 1px solid var(--line);
        background: #e4ece3;
        isolation: isolate;
      }
      .map {
        position: absolute;
        inset: 0;
        cursor: crosshair;
      }
      .count {
        margin: 0;
        font-size: 0.78rem;
        color: var(--muted);
      }
      .stats {
        font-weight: 600;
        color: var(--forest);
      }
      :host ::ng-deep .bw-route-vertex {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: var(--forest);
        border: 2px solid #fff;
        box-shadow: 0 1px 4px rgba(31, 61, 34, 0.35);
      }
      :host ::ng-deep .maplibregl-ctrl-attrib {
        font-size: 9px;
      }
    `,
  ],
})
export class RouteEditorComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  @Input() center: { lat: number; lng: number } | null = null;
  @Input() initialRoute: RouteGeoJson | null = null;

  @Output() readonly routeChange = new EventEmitter<RouteGeoJson | null>();
  /** Distance en mètres du tracé courant (0 si moins de 2 points). */
  @Output() readonly distanceChange = new EventEmitter<number>();

  protected readonly coordinates = signal<RouteLngLat[]>([]);
  protected readonly distanceMeters = signal(0);
  protected readonly estimatedMinutes = signal(0);

  private map?: MapLibreMap;
  private mapReady = false;
  private markers: Marker[] = [];
  private resizeObserver?: ResizeObserver;
  private clickHandler?: (e: MapMouseEvent) => void;

  ngAfterViewInit(): void {
    setWorkerUrl(new URL('maplibre-gl-worker.mjs', document.baseURI).href);

    const start = this.resolveCenter();
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
      center: [start.lng, start.lat],
      zoom: 13,
      minZoom: 4.2,
      maxZoom: 18,
      maxBounds: [
        [-6.5, 40.8],
        [10.5, 51.8],
      ],
    });
    this.map = map;
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');

    this.clickHandler = (e: MapMouseEvent) => {
      const next: RouteLngLat = [e.lngLat.lng, e.lngLat.lat];
      this.coordinates.update((pts) => [...pts, next]);
      this.syncMap();
      this.emitRoute();
    };
    map.on('click', this.clickHandler);

    map.once('load', () => {
      this.mapReady = true;
      map.addSource('route-line', {
        type: 'geojson',
        data: emptyLine(),
      });
      map.addLayer({
        id: 'route-line-layer',
        type: 'line',
        source: 'route-line',
        paint: {
          'line-color': '#2f5d32',
          'line-width': 3.5,
          'line-opacity': 0.9,
        },
      });
      map.resize();
      if (this.initialRoute?.coordinates?.length) {
        this.coordinates.set([...this.initialRoute.coordinates]);
      }
      this.syncMap();
      this.refreshMetrics(false);
      this.flyToCenter();
    });

    this.resizeObserver = new ResizeObserver(() => this.map?.resize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['center'] && !changes['center'].firstChange) {
      this.flyToCenter();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.map && this.clickHandler) {
      this.map.off('click', this.clickHandler);
    }
    this.clearMarkers();
    this.map?.remove();
  }

  protected undo(): void {
    this.coordinates.update((pts) => pts.slice(0, -1));
    this.syncMap();
    this.emitRoute();
  }

  protected clear(): void {
    this.coordinates.set([]);
    this.syncMap();
    this.emitRoute();
  }

  protected formatKm(meters: number): string {
    if (meters < 1000) {
      return `${Math.round(meters)} m`;
    }
    return `${(meters / 1000).toFixed(meters >= 10_000 ? 0 : 1)} km`;
  }

  private emitRoute(): void {
    this.refreshMetrics(true);
    const coords = this.coordinates();
    if (coords.length < 2) {
      this.routeChange.emit(null);
      return;
    }
    this.routeChange.emit({ type: 'LineString', coordinates: coords });
  }

  private refreshMetrics(emitDistance: boolean): void {
    const meters = lineLengthMeters(this.coordinates());
    this.distanceMeters.set(meters);
    this.estimatedMinutes.set(estimateWalkMinutes(meters));
    if (emitDistance) {
      this.distanceChange.emit(meters);
    }
  }

  private syncMap(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    const coords = this.coordinates();
    const source = map.getSource('route-line') as GeoJSONSource | undefined;
    source?.setData(
      coords.length >= 2
        ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } }
        : emptyLine(),
    );

    this.clearMarkers();
    for (const [lng, lat] of coords) {
      const el = document.createElement('div');
      el.className = 'bw-route-vertex';
      this.markers.push(new Marker({ element: el }).setLngLat([lng, lat]).addTo(map));
    }

    if (coords.length >= 2) {
      const bounds = new LngLatBounds();
      for (const [lng, lat] of coords) {
        bounds.extend([lng, lat]);
      }
      map.fitBounds(bounds, { padding: 36, maxZoom: 15, duration: 400 });
    }
  }

  private flyToCenter(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    if (this.coordinates().length > 0) {
      return;
    }
    const c = this.resolveCenter();
    map.flyTo({ center: [c.lng, c.lat], zoom: 13, duration: 500 });
  }

  private resolveCenter(): { lat: number; lng: number } {
    if (
      this.center &&
      Number.isFinite(this.center.lat) &&
      Number.isFinite(this.center.lng)
    ) {
      return this.center;
    }
    return { lat: 46.6, lng: 2.5 };
  }

  private clearMarkers(): void {
    for (const marker of this.markers) {
      marker.remove();
    }
    this.markers = [];
  }
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

/** Allure balade chiens ~3,5 km/h. */
const WALK_SPEED_KMH = 3.5;

export function lineLengthMeters(coords: RouteLngLat[]): number {
  if (coords.length < 2) {
    return 0;
  }
  let total = 0;
  for (let i = 1; i < coords.length; i++) {
    total += haversineMeters(coords[i - 1], coords[i]);
  }
  return total;
}

export function estimateWalkMinutes(meters: number): number {
  if (meters <= 0) {
    return 0;
  }
  const raw = (meters / 1000 / WALK_SPEED_KMH) * 60;
  const rounded = Math.round(raw / 5) * 5;
  return Math.min(180, Math.max(15, rounded));
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
