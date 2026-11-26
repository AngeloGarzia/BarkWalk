-- Tracé de parcours (GeoJSON LineString) pour les balades.
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS route_geojson JSONB;
