-- Étape 4 — Accès, paiements, concurrence, audit, rôles DB
-- Rejouable base vide + peuplée.

-- ---------------------------------------------------------------------------
-- AuditLog append-only côté application (REVOKE UPDATE/DELETE sur rôle app)
-- Colonnes avant/après
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS before_state JSONB,
    ADD COLUMN IF NOT EXISTS after_state JSONB,
    ADD COLUMN IF NOT EXISTS correlation_id UUID;

CREATE OR REPLACE FUNCTION barkwalk_forbid_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only'
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_log_no_update ON audit_log;
CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_forbid_audit_mutation();

-- ---------------------------------------------------------------------------
-- Webhooks — idempotence déjà UNIQUE ; colonnes signature / payload hash
-- ---------------------------------------------------------------------------
ALTER TABLE webhook_events
    ADD COLUMN IF NOT EXISTS signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSED';

ALTER TABLE webhook_events DROP CONSTRAINT IF EXISTS webhook_events_processing_status_check;
ALTER TABLE webhook_events
    ADD CONSTRAINT webhook_events_processing_status_check
    CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED'));

-- ---------------------------------------------------------------------------
-- Payments — immutabilité des montants (corrections via refund fields uniquement)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION barkwalk_payment_immutable_core()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'payments cannot be deleted'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.amount_cents IS DISTINCT FROM OLD.amount_cents
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.booking_id IS DISTINCT FROM OLD.booking_id
       OR NEW.payment_type IS DISTINCT FROM OLD.payment_type THEN
        RAISE EXCEPTION 'payment core fields are immutable; use refund columns'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_payments_immutable ON payments;
CREATE TRIGGER trg_payments_immutable
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_payment_immutable_core();

CREATE OR REPLACE FUNCTION barkwalk_payout_no_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payouts cannot be deleted'
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_payouts_no_delete ON payouts;
CREATE TRIGGER trg_payouts_no_delete
    BEFORE DELETE ON payouts
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_payout_no_delete();

-- ---------------------------------------------------------------------------
-- Settings — bornes métier + journalisation via audit (trigger)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION barkwalk_settings_bounds()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v INT;
BEGIN
    IF NEW.key IN (
        'pending_payment_minutes', 'cancellation_free_hours', 'waitlist_offer_hours',
        'community_walks_threshold', 'community_capacity_max', 'indep_commission_percent',
        'review_window_days', 'login_max_failures', 'login_lock_minutes'
    ) THEN
        v := NEW.value::INT;
        IF v < 0 THEN
            RAISE EXCEPTION 'setting % cannot be negative', NEW.key USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.key = 'indep_commission_percent' AND v > 100 THEN
            RAISE EXCEPTION 'commission percent > 100' USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.key = 'pending_payment_minutes' AND v = 0 THEN
            RAISE EXCEPTION 'pending_payment_minutes cannot be zero' USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.key = 'community_capacity_max' AND v < 1 THEN
            RAISE EXCEPTION 'community_capacity_max must be >= 1' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_settings_bounds ON settings;
CREATE TRIGGER trg_settings_bounds
    BEFORE INSERT OR UPDATE ON settings
    FOR EACH ROW EXECUTE PROCEDURE barkwalk_settings_bounds();

-- ---------------------------------------------------------------------------
-- Abus — limites configurables
-- ---------------------------------------------------------------------------
INSERT INTO settings (key, value, description) VALUES
('limit_bookings_per_day', '20', 'Max réservations créées / user / jour'),
('limit_messages_per_hour', '60', 'Max messages / user / heure'),
('limit_reviews_per_day', '10', 'Max avis / user / jour'),
('limit_reports_per_day', '5', 'Max signalements / user / jour'),
('rate_limit_auth_per_minute', '20', 'Rate limit auth')
ON CONFLICT (key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Concurrence — index support verrou pessimiste déjà via findByIdForUpdate
-- Advisory lock helper commenté pour jobs waitlist / payment expiry
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_bookings_walk_active_capacity
    ON bookings (walk_id)
    WHERE status IN ('CONFIRMED', 'PENDING_PAYMENT');

-- ---------------------------------------------------------------------------
-- Rôles PostgreSQL applicatifs (idempotent ; mots de passe via env ops, pas en repo)
-- ---------------------------------------------------------------------------
-- Mots de passe / LOGIN : configurés hors dépôt (ops). Rôles créés sans secret.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'barkwalk_app') THEN
        CREATE ROLE barkwalk_app NOINHERIT NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'barkwalk_migrator') THEN
        CREATE ROLE barkwalk_migrator NOINHERIT NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'barkwalk_readonly') THEN
        CREATE ROLE barkwalk_readonly NOINHERIT NOLOGIN;
    END IF;
END $$;

GRANT CONNECT ON DATABASE barkwalk TO barkwalk_app, barkwalk_migrator, barkwalk_readonly;
GRANT USAGE ON SCHEMA public TO barkwalk_app, barkwalk_migrator, barkwalk_readonly;

-- Migrator : DDL
GRANT ALL ON SCHEMA public TO barkwalk_migrator;
GRANT ALL ON ALL TABLES IN SCHEMA public TO barkwalk_migrator;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO barkwalk_migrator;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO barkwalk_migrator;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO barkwalk_migrator;

-- App : DML sans DDL ; pas de DELETE/UPDATE sur audit_log
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO barkwalk_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO barkwalk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO barkwalk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO barkwalk_app;
REVOKE UPDATE, DELETE ON audit_log FROM barkwalk_app;
GRANT SELECT, INSERT ON audit_log TO barkwalk_app;

-- Readonly analytique
GRANT SELECT ON ALL TABLES IN SCHEMA public TO barkwalk_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO barkwalk_readonly;
REVOKE ALL ON audit_log FROM barkwalk_readonly;
GRANT SELECT ON audit_log TO barkwalk_readonly;

COMMENT ON ROLE barkwalk_app IS 'Rôle applicatif moindre privilège (pas de DDL, audit append-only)';
COMMENT ON ROLE barkwalk_migrator IS 'Rôle migrations Flyway uniquement';
COMMENT ON ROLE barkwalk_readonly IS 'Lecture seule analytique / export';
