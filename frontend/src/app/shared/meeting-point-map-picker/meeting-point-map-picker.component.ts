// Carte centrée sur la ville : sélection / pose d’un point de rendez-vous.
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
  LngLatBounds,
  Map as MapLibreMap,
  MapMouseEvent,
  Marker,
  NavigationControl,
  Popup,
  setWorkerUrl,
} from 'maplibre-gl';

export interface MeetingPointPin {
  id: number;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

@Component({
  selector: 'app-meeting-point-map-picker',
  standalone: true,
  template: `
    <div class="mp-picker">
      <p class="hint">
        Cliquez un pin existant, ou cliquez la carte pour placer le rendez-vous.
      </p>
      <div class="map-wrap">
        <div #mapContainer class="map" role="application" aria-label="Choisir le point de rendez-vous"></div>
      </div>
      @if (selectedLabel) {
        <p class="selected">{{ selectedLabel }}</p>
      }
    </div>
  `,
  styles: [
    `
      .mp-picker {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }
      .hint {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 500;
        color: var(--muted);
      }
      .map-wrap {
        position: relative;
        height: 13rem;
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
      .selected {
        margin: 0;
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      :host ::ng-deep .bw-mp-marker {
        width: 22px;
        height: 22px;
        border-radius: 50% 50% 50% 0;
        background: var(--forest);
        border: 2px solid #fff;
        box-shadow: 0 2px 8px rgba(31, 61, 34, 0.35);
        transform: rotate(-45deg);
        cursor: pointer;
      }
      :host ::ng-deep .bw-mp-marker.is-selected {
        background: var(--cta);
        box-shadow: 0 0 0 3px rgba(232, 135, 42, 0.35);
      }
      :host ::ng-deep .maplibregl-popup-content {
        font-family: var(--font-body);
        padding: 0.45rem 0.6rem;
        border-radius: 10px;
        font-size: 0.8rem;
      }
      :host ::ng-deep .maplibregl-ctrl-attrib {
        font-size: 9px;
      }
    `,
  ],
})
export class MeetingPointMapPickerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  @Input() center: { lat: number; lng: number } | null = null;
  @Input() meetingPoints: MeetingPointPin[] = [];
  @Input() selectedId: number | null = null;
  @Input() placing = false;

  @Output() readonly meetingPointSelected = new EventEmitter<number>();
  @Output() readonly mapPlace = new EventEmitter<{ lat: number; lng: number }>();

  protected selectedLabel = '';

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
      zoom: 12.5,
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
      if (this.placing) {
        return;
      }
      this.mapPlace.emit({ lat: e.lngLat.lat, lng: e.lngLat.lng });
    };
    map.on('click', this.clickHandler);

    map.once('load', () => {
      this.mapReady = true;
      map.resize();
      this.render();
    });
    this.resizeObserver = new ResizeObserver(() => this.map?.resize());
    this.resizeObserver.observe(this.mapContainer.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['meetingPoints'] ||
      changes['selectedId'] ||
      changes['center'] ||
      changes['placing']
    ) {
      this.render();
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

  private render(): void {
    const map = this.map;
    if (!map || !this.mapReady) {
      return;
    }
    this.clearMarkers();

    const pins = this.meetingPoints.filter(
      (p) => Number.isFinite(p.lat) && Number.isFinite(p.lng),
    );
    const selected = pins.find((p) => p.id === this.selectedId) ?? null;
    this.selectedLabel = selected
      ? `${selected.name} — ${selected.address}`
      : '';

    for (const point of pins) {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = 'bw-mp-marker' + (point.id === this.selectedId ? ' is-selected' : '');
      el.setAttribute('aria-label', point.name);
      el.addEventListener('click', (event) => {
        event.stopPropagation();
        this.meetingPointSelected.emit(point.id);
      });
      const popup = new Popup({ offset: 18, closeButton: false }).setHTML(
        `<strong>${escapeHtml(point.name)}</strong><br/><span>${escapeHtml(point.address)}</span>`,
      );
      this.markers.push(
        new Marker({ element: el, anchor: 'bottom' })
          .setLngLat([point.lng, point.lat])
          .setPopup(popup)
          .addTo(map),
      );
    }

    map.resize();

    if (pins.length >= 2) {
      const bounds = new LngLatBounds();
      for (const p of pins) {
        bounds.extend([p.lng, p.lat]);
      }
      map.fitBounds(bounds, { padding: 40, maxZoom: 14, duration: 500 });
      return;
    }
    if (pins.length === 1) {
      map.flyTo({ center: [pins[0].lng, pins[0].lat], zoom: 14, duration: 500 });
      return;
    }

    const c = this.resolveCenter();
    map.flyTo({ center: [c.lng, c.lat], zoom: 12.5, duration: 500 });
  }

  private resolveCenter(): { lat: number; lng: number } {
    if (this.center && Number.isFinite(this.center.lat) && Number.isFinite(this.center.lng)) {
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

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
