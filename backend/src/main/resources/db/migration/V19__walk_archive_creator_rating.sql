-- Archivage des balades (suppression hard réservée à l'admin).
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_walks_archived_at ON walks (archived_at)
    WHERE archived_at IS NOT NULL;

-- Note moyenne du créateur (agrégat des notes de balades).
ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS average_rating NUMERIC(3, 2),
    ADD COLUMN IF NOT EXISTS ratings_count INTEGER NOT NULL DEFAULT 0;

-- Archive automatique des balades déjà terminées / annulées.
UPDATE walks
SET archived_at = COALESCE(completed_at, updated_at, NOW())
WHERE archived_at IS NULL
  AND status IN ('COMPLETED', 'CANCELLED');
