-- Note participant sur une balade (1–5), ouverte à partir du lendemain.
CREATE TABLE IF NOT EXISTS walk_ratings (
    id              BIGSERIAL PRIMARY KEY,
    walk_id         BIGINT NOT NULL REFERENCES walks(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating          INTEGER NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT walk_ratings_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_walk_ratings_walk_user UNIQUE (walk_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_walk_ratings_walk ON walk_ratings (walk_id);
CREATE INDEX IF NOT EXISTS idx_walk_ratings_user_created ON walk_ratings (user_id, created_at DESC);
