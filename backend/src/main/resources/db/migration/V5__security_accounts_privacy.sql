-- Étape 3 — Sécurité comptes & protection données personnelles
-- Rejouable base vide + peuplée.

-- ---------------------------------------------------------------------------
-- Auth — refresh token famille / rotation / magic login usage unique
-- ---------------------------------------------------------------------------
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS family_id UUID,
    ADD COLUMN IF NOT EXISTS replaced_by_token_id BIGINT REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reused_at TIMESTAMPTZ;

UPDATE refresh_tokens
SET family_id = gen_random_uuid()
WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;
ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET DEFAULT gen_random_uuid();

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family
    ON refresh_tokens (family_id)
    WHERE revoked IS FALSE;

-- magic login : déjà token_hash + used + expires — renforcer défaut expiration courte côté app
ALTER TABLE magic_login_tokens
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMPTZ;

-- Verrouillage compte après échecs
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_algo VARCHAR(40) NOT NULL DEFAULT 'BCRYPT',
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS public_id UUID;

UPDATE users SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE users ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_public_id ON users (public_id);

-- Email normalisé (minuscules) + unicité insensible à la casse
UPDATE users SET email = lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_password_algo_check;
ALTER TABLE users
    ADD CONSTRAINT users_password_algo_check
    CHECK (password_algo IN ('BCRYPT', 'ARGON2ID'));

-- deleted boolean legacy → aligner deleted_at
UPDATE users SET deleted_at = updated_at WHERE deleted IS TRUE AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- ConsentLog — IP tronquée/hachée (jamais IP claire)
-- ---------------------------------------------------------------------------
ALTER TABLE consent_logs
    ADD COLUMN IF NOT EXISTS ip_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS user_agent_hash VARCHAR(128);

-- ---------------------------------------------------------------------------
-- Identifiants publics non énumérables (UUID) sur entités exposées
-- ---------------------------------------------------------------------------
ALTER TABLE dogs ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE dogs SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE dogs ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE dogs ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_dogs_public_id ON dogs (public_id);

ALTER TABLE walks ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE walks SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE walks ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE walks ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_walks_public_id ON walks (public_id);

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE bookings SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE bookings ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_public_id ON bookings (public_id);

ALTER TABLE meeting_points ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE meeting_points SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE meeting_points ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE meeting_points ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_points_public_id ON meeting_points (public_id);

ALTER TABLE conversations ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE conversations SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE conversations ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE conversations ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_public_id ON conversations (public_id);

-- Documents : clé de stockage non devinable (préfixe UUID imposé aux nouvelles — check longueur)
ALTER TABLE dog_documents DROP CONSTRAINT IF EXISTS dog_documents_storage_key_len;
ALTER TABLE dog_documents
    ADD CONSTRAINT dog_documents_storage_key_len CHECK (char_length(storage_key) >= 16);

ALTER TABLE walker_documents DROP CONSTRAINT IF EXISTS walker_documents_storage_key_len;
ALTER TABLE walker_documents
    ADD CONSTRAINT walker_documents_storage_key_len CHECK (char_length(storage_key) >= 16);

-- ---------------------------------------------------------------------------
-- RGPD — durées de conservation (config) + vue export données perso
-- ---------------------------------------------------------------------------
INSERT INTO settings (key, value, description) VALUES
('retention_inactive_account_days', '1095', 'RGPD conservation comptes inactifs (jours)'),
('retention_messages_days', '730', 'RGPD conservation messages (jours)'),
('retention_dog_documents_days_after_delete', '365', 'RGPD docs chien après suppression compte'),
('login_max_failures', '5', 'Verrouillage après N échecs'),
('login_lock_minutes', '15', 'Durée verrouillage (minutes)')
ON CONFLICT (key) DO NOTHING;

CREATE OR REPLACE VIEW v_user_personal_data_export AS
SELECT
    u.public_id AS user_public_id,
    u.email,
    u.email_verified,
    u.created_at AS account_created_at,
    p.first_name,
    p.last_name,
    p.phone,
    p.bio,
    p.city_id,
    p.community_org_status,
    p.completed_walks_count,
    (SELECT jsonb_agg(jsonb_build_object(
        'public_id', d.public_id,
        'name', d.name,
        'breed', d.breed,
        'dossier_status', d.dossier_status
     )) FROM dogs d WHERE d.owner_id = u.id) AS dogs,
    (SELECT jsonb_agg(jsonb_build_object(
        'public_id', b.public_id,
        'status', b.status,
        'created_at', b.created_at
     )) FROM bookings b WHERE b.user_id = u.id) AS bookings,
    (SELECT jsonb_agg(jsonb_build_object(
        'document_type', c.document_type,
        'version', c.version,
        'accepted_at', c.accepted_at
     )) FROM consent_logs c WHERE c.user_id = u.id) AS consents
FROM users u
LEFT JOIN profiles p ON p.user_id = u.id;

COMMENT ON VIEW v_user_personal_data_export IS 'Export RGPD des données personnelles (sans secrets ni hashes)';

-- Fonction d''anonymisation compte (appelée par job / admin)
CREATE OR REPLACE FUNCTION barkwalk_anonymize_user(p_user_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_public UUID;
BEGIN
    SELECT public_id INTO v_public FROM users WHERE id = p_user_id;
    IF v_public IS NULL THEN
        RAISE EXCEPTION 'user not found';
    END IF;

    UPDATE users
    SET email = 'deleted+' || replace(v_public::text, '-', '') || '@anonymized.invalid',
        password_hash = NULL,
        enabled = FALSE,
        deleted = TRUE,
        deleted_at = NOW(),
        anonymized_at = NOW(),
        updated_at = NOW()
    WHERE id = p_user_id;

    UPDATE profiles
    SET first_name = 'Membre',
        last_name = 'supprimé',
        phone = NULL,
        bio = NULL,
        photo_key = NULL,
        updated_at = NOW()
    WHERE user_id = p_user_id;

    -- Révoquer toute la famille de refresh tokens
    UPDATE refresh_tokens SET revoked = TRUE WHERE user_id = p_user_id AND revoked IS FALSE;
END;
$$;
