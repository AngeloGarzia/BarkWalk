-- INDEP_GUIDED autorisé + balades dédiées chiens catégorie 1/2.

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_walk_type_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_walk_type_check
    CHECK (walk_type IN ('STAFF_GUIDED', 'INDEP_GUIDED', 'COMMUNITY'));

ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS allows_danger_categories BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN walks.allows_danger_categories IS
    'true = balade dédiée chiens catégorie 1/2 uniquement';
