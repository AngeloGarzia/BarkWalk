import type { StyleSpecification } from 'maplibre-gl';
import { environment } from '../../../environments/environment';

export const BASEMAP_SOURCE_ID = 'basemap';
export const BASEMAP_LAYER_ID = 'basemap';

/**
 * Style MapLibre partagé.
 * - Avec `environment.mapTilerApiKey` → MapTiler Streets (clé requise).
 * - Sinon → OpenFreeMap Liberty (gratuit, sans clé).
 */
export function basemapStyle(): string | StyleSpecification {
  const key = environment.mapTilerApiKey?.trim();
  if (key) {
    return `https://api.maptiler.com/maps/streets-v2/style.json?key=${encodeURIComponent(key)}`;
  }
  return 'https://tiles.openfreemap.org/styles/liberty';
}
