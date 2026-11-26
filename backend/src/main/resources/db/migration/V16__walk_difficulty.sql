-- Difficulté balade 1–10 : durée + dénivelé + type de terrain.
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS elevation_gain_m INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS terrain_type VARCHAR(30) NOT NULL DEFAULT 'FLAT',
    ADD COLUMN IF NOT EXISTS difficulty_level SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_elevation_gain_m_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_elevation_gain_m_check
    CHECK (elevation_gain_m >= 0 AND elevation_gain_m <= 3000);

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_terrain_type_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_terrain_type_check
    CHECK (terrain_type IN ('FLAT', 'PARK', 'FOREST', 'TRAIL', 'MOUNTAIN'));

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_difficulty_level_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_difficulty_level_check
    CHECK (difficulty_level BETWEEN 1 AND 10);

COMMENT ON COLUMN walks.elevation_gain_m IS 'Dénivelé positif approximatif en mètres';
COMMENT ON COLUMN walks.terrain_type IS 'FLAT|PARK|FOREST|TRAIL|MOUNTAIN';
COMMENT ON COLUMN walks.difficulty_level IS 'Score 1–10 calculé (durée, dénivelé, terrain)';
