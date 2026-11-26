-- Statut live : organisateur / encadrant peut démarrer une balade (suivi GPS côté app).
ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_status_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'IN_PROGRESS', 'CANCELLED', 'COMPLETED'));

ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_walks_in_progress
    ON walks (status)
    WHERE status = 'IN_PROGRESS';
