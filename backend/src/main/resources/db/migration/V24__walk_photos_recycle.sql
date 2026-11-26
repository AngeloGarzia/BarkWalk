-- Photos de balade (promo / galerie) + lien de recyclage.
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS recycled_from_walk_id BIGINT REFERENCES walks(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_walks_recycled_from ON walks (recycled_from_walk_id)
    WHERE recycled_from_walk_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS walk_photos (
    id              BIGSERIAL PRIMARY KEY,
    walk_id         BIGINT NOT NULL REFERENCES walks(id) ON DELETE CASCADE,
    uploaded_by     BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    storage_key     VARCHAR(255) NOT NULL,
    content_type    VARCHAR(80) NOT NULL DEFAULT 'image/jpeg',
    kind            VARCHAR(20) NOT NULL,
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    caption         VARCHAR(240),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT walk_photos_kind_check CHECK (kind IN ('PROMO', 'GALLERY')),
    CONSTRAINT walk_photos_sort_nonneg CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_walk_photos_walk_kind
    ON walk_photos (walk_id, kind, sort_order);

COMMENT ON COLUMN walks.recycled_from_walk_id IS 'Balade source recyclée (héritage attributs ; photos/notes via history)';
COMMENT ON TABLE walk_photos IS 'PROMO max 6 (création) ; GALLERY max 20 (pendant/après, org+participants)';
