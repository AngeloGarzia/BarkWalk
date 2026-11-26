-- Aligne les balades existantes sur les règles métier récentes :
-- créateur (organizer_user_id), dénivelé/terrain/difficulté 1–10, archivage.

-- Créateur : encadrées → user du walker assigné ; communautaires déjà pourvues.
UPDATE walks w
SET organizer_user_id = wp.user_id,
    updated_at = NOW()
FROM walker_profiles wp
WHERE w.walker_id = wp.id
  AND w.organizer_user_id IS NULL
  AND w.walk_type IN ('STAFF_GUIDED', 'INDEP_GUIDED');

-- Profils terrain / dénivelé réalistes (démo France) puis difficulté recalculée.
UPDATE walks SET elevation_gain_m = 40,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Balade matinale Tête d''Or';
UPDATE walks SET elevation_gain_m = 15,  terrain_type = 'FLAT',     updated_at = NOW()
WHERE title = 'Balade communautaire Bellecour';
UPDATE walks SET elevation_gain_m = 25,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Balade Tuileries';
UPDATE walks SET elevation_gain_m = 160, terrain_type = 'TRAIL',    updated_at = NOW()
WHERE title = 'Buttes-Chaumont canin';
UPDATE walks SET elevation_gain_m = 55,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Balade Borély';
UPDATE walks SET elevation_gain_m = 30,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Toulouse Jardin des Plantes';
UPDATE walks SET elevation_gain_m = 20,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Balade Jardin Public Bordeaux';
UPDATE walks SET elevation_gain_m = 35,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Nantes Île de Versailles';
UPDATE walks SET elevation_gain_m = 25,  terrain_type = 'PARK',     updated_at = NOW()
WHERE title = 'Orangerie Strasbourg';
UPDATE walks SET elevation_gain_m = 70,  terrain_type = 'FOREST',   updated_at = NOW()
WHERE title = 'Balade Citadelle Lille';
UPDATE walks SET elevation_gain_m = 10,  terrain_type = 'FLAT',     updated_at = NOW()
WHERE title = 'Montpellier centre';
UPDATE walks SET elevation_gain_m = 220, terrain_type = 'MOUNTAIN', updated_at = NOW()
WHERE title = 'Grenoble Paul Mistral';
UPDATE walks SET elevation_gain_m = 20,  terrain_type = 'FLAT',     updated_at = NOW()
WHERE title = 'L''etang de thau';

-- Fallback : balades encore à 0 / FLAT → estimation depuis la durée.
UPDATE walks
SET elevation_gain_m = CASE
        WHEN duration_minutes >= 90 THEN 180
        WHEN duration_minutes >= 60 THEN 80
        WHEN duration_minutes >= 45 THEN 40
        ELSE 20
    END,
    terrain_type = CASE
        WHEN duration_minutes >= 90 THEN 'TRAIL'
        WHEN duration_minutes >= 60 THEN 'FOREST'
        WHEN duration_minutes >= 45 THEN 'PARK'
        ELSE 'FLAT'
    END,
    updated_at = NOW()
WHERE elevation_gain_m = 0
  AND terrain_type = 'FLAT';

-- Difficulté = f(durée, dénivelé, terrain) — même pondération 40/40/20 que WalkDifficultyCalculator.
UPDATE walks w
SET difficulty_level = GREATEST(1, LEAST(10, ROUND(
        (CASE
            WHEN w.duration_minutes <= 20 THEN 1
            WHEN w.duration_minutes <= 35 THEN 2
            WHEN w.duration_minutes <= 50 THEN 3
            WHEN w.duration_minutes <= 70 THEN 5
            WHEN w.duration_minutes <= 90 THEN 6
            WHEN w.duration_minutes <= 120 THEN 8
            ELSE 10
         END) * 0.40
      + (CASE
            WHEN w.elevation_gain_m <= 20 THEN 1
            WHEN w.elevation_gain_m <= 60 THEN 2
            WHEN w.elevation_gain_m <= 120 THEN 4
            WHEN w.elevation_gain_m <= 200 THEN 6
            WHEN w.elevation_gain_m <= 350 THEN 8
            ELSE 10
         END) * 0.40
      + (CASE w.terrain_type
            WHEN 'FLAT' THEN 1
            WHEN 'PARK' THEN 2
            WHEN 'FOREST' THEN 4
            WHEN 'TRAIL' THEN 7
            WHEN 'MOUNTAIN' THEN 10
            ELSE 1
         END) * 0.20
    )::numeric)),
    updated_at = NOW();

-- Archivage : terminées / annulées.
UPDATE walks
SET archived_at = COALESCE(archived_at, completed_at, updated_at, NOW()),
    updated_at = NOW()
WHERE archived_at IS NULL
  AND status IN ('COMPLETED', 'CANCELLED');

-- Doublons démo Bellecour : conserve le plus ancien, archive les autres.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY city_id, title ORDER BY id) AS rn
    FROM walks
    WHERE title = 'Balade communautaire Bellecour'
)
UPDATE walks w
SET status = 'CANCELLED',
    cancellation_reason = 'demo_duplicate',
    archived_at = COALESCE(w.archived_at, NOW()),
    updated_at = NOW()
FROM ranked r
WHERE w.id = r.id
  AND r.rn > 1
  AND w.archived_at IS NULL;
