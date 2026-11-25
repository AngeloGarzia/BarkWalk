-- Étape 2 — Règles métier R1–R16 (modèle)
-- Décision R4 (annulation tardive « comptée ») : LES DEUX
--   counts_toward_org_threshold = true  (seuil 3 balades organisateur)
--   counts_toward_membership_quota = true  (quota / forfait adhésion)
-- Rejouable base vide + base peuplée.

-- ---------------------------------------------------------------------------
-- R6 — libération paiement 15 min
-- ---------------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMPTZ;

UPDATE bookings
SET payment_expires_at = created_at + INTERVAL '15 minutes'
WHERE status = 'PENDING_PAYMENT' AND payment_expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_payment_expires
    ON bookings (payment_expires_at)
    WHERE status = 'PENDING_PAYMENT';

-- ---------------------------------------------------------------------------
-- R4 — annulation gratuite / tardive + auteur + motif + « comptée »
-- Statuts : CANCELLED_FREE | CANCELLED_LATE (en plus de CANCELLED legacy → FREE)
-- ---------------------------------------------------------------------------
UPDATE bookings SET status = 'CANCELLED_FREE' WHERE status = 'CANCELLED';

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
    CHECK (status IN (
        'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED_FREE', 'CANCELLED_LATE',
        'COMPLETED', 'NO_SHOW', 'WAITLISTED'
    ));

-- Index partiel étape 1 utilisait CANCELLED — le recréer
DROP INDEX IF EXISTS uq_bookings_walk_dog_active;
CREATE UNIQUE INDEX uq_bookings_walk_dog_active
    ON bookings (walk_id, dog_id)
    WHERE status NOT IN ('CANCELLED_FREE', 'CANCELLED_LATE');

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    ADD COLUMN IF NOT EXISTS cancelled_by_role VARCHAR(40),
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS counts_toward_org_threshold BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS counts_toward_membership_quota BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_cancelled_by_role_check;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_cancelled_by_role_check
    CHECK (
        cancelled_by_role IS NULL
        OR cancelled_by_role IN ('USER', 'ORGANIZER', 'WALKER', 'ADMIN', 'SYSTEM')
    );

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_late_cancel_counts_check;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_late_cancel_counts_check
    CHECK (
        status <> 'CANCELLED_LATE'
        OR (
            counts_toward_org_threshold IS TRUE
            AND counts_toward_membership_quota IS TRUE
        )
    );

-- ---------------------------------------------------------------------------
-- R13 / M9 — avis enrichis
-- ---------------------------------------------------------------------------
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS review_type VARCHAR(40) NOT NULL DEFAULT 'WALK_TOGETHER',
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMPTZ;

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_type_check;
ALTER TABLE reviews
    ADD CONSTRAINT reviews_type_check
    CHECK (review_type IN ('WALKER_OR_ORGANIZER', 'WALK_TOGETHER'));

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_moderation_check;
ALTER TABLE reviews
    ADD CONSTRAINT reviews_moderation_check
    CHECK (moderation_status IN ('VISIBLE', 'HIDDEN'));

-- Fenêtre 14 jours vérifiable via walk.starts_at (contrainte déclenchée à l'insert app + CHECK soft via trigger)
CREATE OR REPLACE FUNCTION barkwalk_review_within_window()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    walk_start TIMESTAMPTZ;
    window_days INT := 14;
BEGIN
    SELECT starts_at INTO walk_start FROM walks WHERE id = NEW.walk_id;
    IF walk_start IS NULL THEN
        RAISE EXCEPTION 'walk not found for review';
    END IF;
    IF NEW.created_at > walk_start + make_interval(days => window_days) THEN
        RAISE EXCEPTION 'review outside % day window', window_days
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.created_at < walk_start THEN
        RAISE EXCEPTION 'review before walk start'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reviews_window ON reviews;
CREATE TRIGGER trg_reviews_window
    BEFORE INSERT ON reviews
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_review_within_window();

-- ---------------------------------------------------------------------------
-- R15 / R16 — WalkerDocument + rappels documents
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS walker_documents (
    id                  BIGSERIAL PRIMARY KEY,
    walker_id           BIGINT NOT NULL REFERENCES walker_profiles(id) ON DELETE CASCADE ON UPDATE CASCADE,
    doc_type            VARCHAR(40) NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    expires_at          DATE,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    validated_by        BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    validated_at        TIMESTAMPTZ,
    refusal_reason      TEXT,
    reminder_j30_sent_at TIMESTAMPTZ,
    reminder_j7_sent_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT walker_documents_doc_type_check
        CHECK (doc_type IN ('DIPLOMA', 'SIRET', 'RC_PRO', 'CHARTER', 'OTHER')),
    CONSTRAINT walker_documents_status_check
        CHECK (status IN ('PENDING', 'VALIDATED', 'REFUSED', 'EXPIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_walker_documents_active_type
    ON walker_documents (walker_id, doc_type)
    WHERE status IN ('PENDING', 'VALIDATED');

DROP TRIGGER IF EXISTS trg_walker_documents_updated_at ON walker_documents;
CREATE TRIGGER trg_walker_documents_updated_at
    BEFORE UPDATE ON walker_documents
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_set_updated_at();

-- Migrer clés legacy walker_profiles → documents (si présentes)
INSERT INTO walker_documents (walker_id, doc_type, storage_key, expires_at, status, validated_at)
SELECT wp.id, 'DIPLOMA', wp.diploma_key, NULL,
       CASE WHEN wp.status = 'VALIDATED' THEN 'VALIDATED' ELSE 'PENDING' END,
       CASE WHEN wp.status = 'VALIDATED' THEN wp.created_at ELSE NULL END
FROM walker_profiles wp
WHERE wp.diploma_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM walker_documents wd
      WHERE wd.walker_id = wp.id AND wd.doc_type = 'DIPLOMA'
        AND wd.status IN ('PENDING', 'VALIDATED')
  );

INSERT INTO walker_documents (walker_id, doc_type, storage_key, expires_at, status)
SELECT wp.id, 'RC_PRO', wp.rc_pro_key, wp.rc_pro_expires_at,
       CASE WHEN wp.status = 'VALIDATED' THEN 'VALIDATED' ELSE 'PENDING' END
FROM walker_profiles wp
WHERE wp.rc_pro_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM walker_documents wd
      WHERE wd.walker_id = wp.id AND wd.doc_type = 'RC_PRO'
        AND wd.status IN ('PENDING', 'VALIDATED')
  );

ALTER TABLE dog_documents
    ADD COLUMN IF NOT EXISTS reminder_j30_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reminder_j7_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS validated_by BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refusal_reason TEXT;

-- Dog dossier : validateurs
ALTER TABLE dogs
    ADD COLUMN IF NOT EXISTS validated_by BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- R10 / R8 — statut organisateur unique + historique
-- ---------------------------------------------------------------------------
ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS community_org_status VARCHAR(30) NOT NULL DEFAULT 'NONE';

UPDATE profiles
SET community_org_status = CASE
    WHEN community_org_revoked THEN 'REVOKED'
    WHEN community_org_enabled THEN 'ACTIVE'
    WHEN completed_walks_count >= 3 THEN 'ELIGIBLE'
    ELSE 'NONE'
END;

ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_community_org_status_check;
ALTER TABLE profiles
    ADD CONSTRAINT profiles_community_org_status_check
    CHECK (community_org_status IN ('NONE', 'ELIGIBLE', 'ACTIVE', 'REVOKED'));

ALTER TABLE profiles DROP COLUMN IF EXISTS community_org_enabled;
ALTER TABLE profiles DROP COLUMN IF EXISTS community_org_revoked;

CREATE TABLE IF NOT EXISTS community_org_status_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    changed_by      BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    reason          TEXT,
    automatic       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT community_org_history_to_check
        CHECK (to_status IN ('NONE', 'ELIGIBLE', 'ACTIVE', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_community_org_history_user
    ON community_org_status_history (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Incidents séparés du compte-rendu
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS incidents (
    id                  BIGSERIAL PRIMARY KEY,
    walk_id             BIGINT NOT NULL REFERENCES walks(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    walk_report_id      BIGINT REFERENCES walk_reports(id) ON DELETE SET NULL ON UPDATE CASCADE,
    reported_by         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    incident_type       VARCHAR(40) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    description         TEXT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    handled_by          BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    handled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT incidents_type_check CHECK (incident_type IN (
        'BITE', 'FIGHT', 'INJURY', 'LOST_DOG', 'BEHAVIOR', 'OTHER'
    )),
    CONSTRAINT incidents_severity_check CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT incidents_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED'))
);

DROP TRIGGER IF EXISTS trg_incidents_updated_at ON incidents;
CREATE TRIGGER trg_incidents_updated_at
    BEFORE UPDATE ON incidents
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_set_updated_at();

-- Migrer incidents embarqués dans walk_reports
INSERT INTO incidents (walk_id, walk_report_id, reported_by, incident_type, severity, description, status, created_at)
SELECT wr.walk_id, wr.id, wr.author_id,
       CASE
           WHEN wr.incident_type IN ('BITE', 'FIGHT', 'INJURY', 'LOST_DOG', 'BEHAVIOR', 'OTHER')
               THEN wr.incident_type
           ELSE 'OTHER'
       END,
       CASE
           WHEN wr.incident_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
               THEN wr.incident_severity
           ELSE 'MEDIUM'
       END,
       COALESCE(NULLIF(wr.incident_description, ''), 'Migré depuis walk_reports'),
       'RESOLVED',
       wr.created_at
FROM walk_reports wr
WHERE wr.incident_type IS NOT NULL OR wr.incident_description IS NOT NULL;

ALTER TABLE walk_reports
    DROP COLUMN IF EXISTS incident_type,
    DROP COLUMN IF EXISTS incident_description,
    DROP COLUMN IF EXISTS incident_severity;

-- ---------------------------------------------------------------------------
-- Messagerie — lecture / notification par membre
-- ---------------------------------------------------------------------------
ALTER TABLE conversation_members
    ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_notified_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- Walk — annulation, récurrence occurrence, disclaimer dérivable
-- ---------------------------------------------------------------------------
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS recurrence_rule_id BIGINT REFERENCES walk_recurrence_rules(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- community_disclaimer_required : déductible du type → colonne retirée
ALTER TABLE walks DROP COLUMN IF EXISTS community_disclaimer_required;

-- ---------------------------------------------------------------------------
-- Attendance / Report
-- ---------------------------------------------------------------------------
ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS recorded_by BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS conversation_id BIGINT REFERENCES conversations(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- ---------------------------------------------------------------------------
-- Payment — remboursements
-- ---------------------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_amount_cents INTEGER,
    ADD COLUMN IF NOT EXISTS refund_provider_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_reason TEXT;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_refund_amount_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_refund_amount_check
    CHECK (
        refund_amount_cents IS NULL
        OR (refund_amount_cents >= 0 AND refund_amount_cents <= amount_cents)
    );

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_commission_lte_amount;
ALTER TABLE payments
    ADD CONSTRAINT payments_commission_lte_amount
    CHECK (commission_cents <= amount_cents);

-- ---------------------------------------------------------------------------
-- EmailTemplate
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_templates (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(80) NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body_html       TEXT NOT NULL,
    body_text       TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    version         VARCHAR(20) NOT NULL DEFAULT '1',
    updated_by      BIGINT REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DROP TRIGGER IF EXISTS trg_email_templates_updated_at ON email_templates;
CREATE TRIGGER trg_email_templates_updated_at
    BEFORE UPDATE ON email_templates
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_set_updated_at();

INSERT INTO email_templates (code, name, subject, body_html, body_text) VALUES
('BOOKING_CONFIRMED', 'Confirmation réservation', 'Votre balade est confirmée', '<p>Bonjour, votre réservation est confirmée.</p>', 'Votre réservation est confirmée.'),
('PAYMENT_REMINDER', 'Rappel paiement', 'Finalisez votre paiement', '<p>Votre place expire bientôt.</p>', 'Votre place expire bientôt.'),
('DOC_EXPIRY_J30', 'Document expire J-30', 'Un document expire dans 30 jours', '<p>Merci de renouveler votre document.</p>', 'Merci de renouveler votre document.'),
('DOC_EXPIRY_J7', 'Document expire J-7', 'Un document expire dans 7 jours', '<p>Dernier rappel document.</p>', 'Dernier rappel document.')
ON CONFLICT (code) DO NOTHING;

-- Setting délai paiement (R6)
INSERT INTO settings (key, value, description) VALUES
('pending_payment_minutes', '15', 'R6 Libération place non payée (minutes)')
ON CONFLICT (key) DO NOTHING;
