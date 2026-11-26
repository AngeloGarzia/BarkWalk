-- Photos profil et chien : 1 à 3 images par entité (caméra ou fichier).

CREATE TABLE profile_photos (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    storage_key     VARCHAR(255) NOT NULL,
    content_type    VARCHAR(80) NOT NULL DEFAULT 'image/jpeg',
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT profile_photos_sort_order_check CHECK (sort_order BETWEEN 0 AND 2),
    CONSTRAINT profile_photos_storage_key_len CHECK (char_length(storage_key) >= 8)
);

CREATE INDEX idx_profile_photos_user ON profile_photos (user_id);

CREATE TABLE dog_photos (
    id              BIGSERIAL PRIMARY KEY,
    dog_id          BIGINT NOT NULL REFERENCES dogs(id) ON DELETE CASCADE ON UPDATE CASCADE,
    storage_key     VARCHAR(255) NOT NULL,
    content_type    VARCHAR(80) NOT NULL DEFAULT 'image/jpeg',
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT dog_photos_sort_order_check CHECK (sort_order BETWEEN 0 AND 2),
    CONSTRAINT dog_photos_storage_key_len CHECK (char_length(storage_key) >= 8)
);

CREATE INDEX idx_dog_photos_dog ON dog_photos (dog_id);

-- Migrer l'ancienne photo unique profil → slot 0 si présente
INSERT INTO profile_photos (user_id, storage_key, content_type, sort_order)
SELECT p.user_id, p.photo_key, 'image/jpeg', 0
FROM profiles p
WHERE p.photo_key IS NOT NULL
  AND char_length(p.photo_key) >= 8
ON CONFLICT DO NOTHING;

-- Migrer l'ancienne photo unique chien → slot 0 si présente
INSERT INTO dog_photos (dog_id, storage_key, content_type, sort_order)
SELECT d.id, d.photo_key, 'image/jpeg', 0
FROM dogs d
WHERE d.photo_key IS NOT NULL
  AND char_length(d.photo_key) >= 8
ON CONFLICT DO NOTHING;
