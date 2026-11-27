// Petit indicateur « parcours GPS » si la balade a une LineString avec points.
import { Component, Input } from '@angular/core';

export type RouteGeoJsonLike =
  | { type?: string; coordinates?: unknown }
  | string
  | null
  | undefined;

/** True si routeGeojson contient une LineString avec au moins 2 points GPS. */
export function hasWalkRouteTrack(raw: RouteGeoJsonLike): boolean {
  if (raw == null) {
    return false;
  }
  try {
    const parsed = typeof raw === 'string' ? (JSON.parse(raw) as { type?: string; coordinates?: unknown }) : raw;
    if (!parsed || typeof parsed !== 'object') {
      return false;
    }
    if (parsed.type !== 'LineString' || !Array.isArray(parsed.coordinates)) {
      return false;
    }
    const points = parsed.coordinates.filter(
      (c): c is [number, number] =>
        Array.isArray(c) &&
        c.length >= 2 &&
        Number.isFinite(Number(c[0])) &&
        Number.isFinite(Number(c[1])),
    );
    return points.length >= 2;
  } catch {
    return false;
  }
}

@Component({
  selector: 'app-walk-route-badge',
  standalone: true,
  template: `
    @if (visible) {
      <span
        class="route-badge"
        title="Parcours GPS renseigné"
        aria-label="Parcours GPS renseigné"
      >
        <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
          <path
            d="M4 17.5 9.2 10l3.3 4.2L17 7.5"
            fill="none"
            stroke="currentColor"
            stroke-width="2.1"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle cx="17" cy="7.5" r="2.1" fill="currentColor" />
          <circle cx="4" cy="17.5" r="1.7" fill="currentColor" />
        </svg>
      </span>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        vertical-align: middle;
      }
      .route-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.35rem;
        height: 1.35rem;
        border-radius: 0.35rem;
        background: color-mix(in srgb, var(--forest) 14%, #fff);
        color: var(--forest-deep);
        border: 1px solid color-mix(in srgb, var(--forest) 28%, transparent);
        flex-shrink: 0;
      }
    `,
  ],
})
export class WalkRouteBadgeComponent {
  protected visible = false;

  @Input({ required: true })
  set routeGeojson(value: RouteGeoJsonLike) {
    this.visible = hasWalkRouteTrack(value);
  }
}
