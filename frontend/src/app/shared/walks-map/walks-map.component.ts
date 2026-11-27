// Carte MapLibre centrée sur une liste de balades géolocalisées.
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
} from '@angular/core';
import {
  GeoJSONSource,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  Popup,
  setWorkerUrl,
} from 'maplibre-gl';
import { basemapStyle } from '../map/basemap-style';

export interface WalkMapPin {
  id: number;
  title: string;
  cityName?: string | null;
  lat: number | null;
  lng: number | null;
  routeGeojson?: { type: string; coordinates: [number, number][] } | string | null;
}

@Component({
  selector: 'app-walks-map',
  standalone: true,
  template: `
    <div class="walks-map" [class.is-empty]="!hasPins">
      <div #mapContainer class="map" role="presentation"></div>
      @if (!hasPins) {
        <p class="map-status">Aucun pin géolocalisé</p>
      }
    </div>
  `,
  styles: [
    `
      .walks-map {
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
      :host ::ng-deep .maplibregl-popup-content {
        font-family: var(--font-body);
        padding: 0.5rem 0.65rem;
        border-radius: 10px;
        font-size: 0.82rem;
      }
      :host ::ng-deep .maplibregl-ctrl-attrib {
        font-size: 9px;
      }
    `,
  ],
})
export class WalksMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  @Input() walks: WalkMapPin[] = [];
  @Input() fallbackCenter: { lat: number; lng: number } | null = null;
  @Input() cityFocus = true;
  @Input() selectedWalkId: number | null = null;

  @Output() readonly pinSelected = new EventEmitter<number>();

  protected hasPins = false;

  private map?: MapLibreMap;
  private markers: Marker[] = [];
  private mapReady = false;
  private resizeObserver?: ResizeObserver;
  private readonly routeSourceId = 'selected-walk-route';
  private readonly routeLayerId = 'selected-walk-route-line';

  ngAfterViewInit(): void {
    setWorkerUrl(new URL('maplibre-gl-worker.mjs', document.baseURI).href);

    const map = new MapLibreMap({
      container: this.mapContainer.nativeElement,
      style: basemapStyle(),
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
      map.addSource(this.routeSourceId, {
        type: 'geojson',
        data: emptyRouteFeature(),
      });
      map.addLayer({
        id: this.routeLayerId,
        type: 'line',
        source: this.routeSourceId,
        paint: {
          'line-color': '#e8872a',
          'line-width': 4,
          'line-opacity': 0.92,
        },
      });
      map.resize();
      this.render();
    });
    this.resizeObserver = new ResizeObserver(() => this.map?.resize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['walks'] ||
      changes['fallbackCenter'] ||
      changes['cityFocus'] ||
      changes['selectedWalkId']
    ) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.clearMarkers();
    this.map?.remove();
  }

  private render(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    this.clearMarkers();

    const pins = this.walks.filter(
      (w) => w.lat != null && w.lng != null && Number.isFinite(w.lat) && Number.isFinite(w.lng),
    );
    this.hasPins = pins.length > 0;

    for (const walk of pins) {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = 'bw-marker';
      el.setAttribute('aria-label', `${walk.title} — ${walk.cityName ?? ''}`);
      el.addEventListener('click', (event) => {
        event.stopPropagation();
        map.flyTo({
          center: [walk.lng!, walk.lat!],
          zoom: Math.max(map.getZoom(), 12),
          duration: 700,
        });
        this.pinSelected.emit(walk.id);
      });

      const popup = new Popup({ offset: 18, closeButton: false }).setHTML(
        `<strong>${escapeHtml(walk.title)}</strong><br/><span>${escapeHtml(walk.cityName ?? '')}</span>`,
      );

      this.markers.push(
        new Marker({ element: el, anchor: 'bottom' })
          .setLngLat([walk.lng!, walk.lat!])
          .setPopup(popup)
          .addTo(map),
      );
    }

    map.resize();
    const selectedRoute = this.updateSelectedRoute(map);

    if (selectedRoute && selectedRoute.length >= 2) {
      const bounds = new LngLatBounds();
      for (const [lng, lat] of selectedRoute) {
        bounds.extend([lng, lat]);
      }
      for (const walk of pins) {
        if (walk.id === this.selectedWalkId) {
          bounds.extend([walk.lng!, walk.lat!]);
        }
      }
      map.fitBounds(bounds, {
        padding: 48,
        maxZoom: 15,
        duration: 700,
      });
      return;
    }

    if (pins.length >= 2) {
      const bounds = new LngLatBounds();
      for (const walk of pins) {
        bounds.extend([walk.lng!, walk.lat!]);
      }
      map.fitBounds(bounds, {
        padding: this.cityFocus ? 48 : 36,
        maxZoom: this.cityFocus ? 13 : 5.8,
        duration: 900,
      });
      return;
    }
    if (pins.length === 1) {
      map.flyTo({
        center: [pins[0].lng!, pins[0].lat!],
        zoom: this.cityFocus ? 13 : 10,
        duration: 900,
      });
      return;
    }

    const fallback = this.fallbackCenter;
    if (
      this.cityFocus &&
      fallback &&
      Number.isFinite(fallback.lat) &&
      Number.isFinite(fallback.lng)
    ) {
      map.flyTo({ center: [fallback.lng, fallback.lat], zoom: 12, duration: 900 });
    }
  }

  private updateSelectedRoute(map: MapLibreMap): [number, number][] | null {
    const source = map.getSource(this.routeSourceId) as GeoJSONSource | undefined;
    if (!source) {
      return null;
    }
    const walk = this.walks.find((w) => w.id === this.selectedWalkId);
    const coords = parseRouteCoordinates(walk?.routeGeojson ?? null);
    if (!coords || coords.length < 2) {
      source.setData(emptyRouteFeature());
      return null;
    }
    source.setData({
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates: coords },
    });
    return coords;
  }

  private clearMarkers(): void {
    for (const marker of this.markers) {
      marker.remove();
    }
    this.markers = [];
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function emptyRouteFeature(): {
  type: 'Feature';
  properties: Record<string, never>;
  geometry: { type: 'LineString'; coordinates: [number, number][] };
} {
  return {
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates: [] },
  };
}

function parseRouteCoordinates(
  raw: { type?: string; coordinates?: unknown } | string | null | undefined,
): [number, number][] | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed =
      typeof raw === 'string' ? (JSON.parse(raw) as { type?: string; coordinates?: unknown }) : raw;
    if (parsed.type !== 'LineString' || !Array.isArray(parsed.coordinates)) {
      return null;
    }
    const coords: [number, number][] = [];
    for (const point of parsed.coordinates) {
      if (
        Array.isArray(point) &&
        point.length >= 2 &&
        typeof point[0] === 'number' &&
        typeof point[1] === 'number'
      ) {
        coords.push([point[0], point[1]]);
      }
    }
    return coords.length >= 2 ? coords : null;
  } catch {
    return null;
  }
}
