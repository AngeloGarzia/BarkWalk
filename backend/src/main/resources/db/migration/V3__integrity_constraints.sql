-- Étape 1 — Intégrité de base (horodatage, CHECK, unicités, FK, index)
-- Rejouable sur base vide et sur base déjà peuplée (V1+V2 + démo).
-- Plafond communautaire aligné sur setting community_capacity_max = 8.

-- ---------------------------------------------------------------------------
-- 0. Fonction updated_at
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION barkwalk_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 1. Horodatages manquants (DEFAULT puis backfill implicite via DEFAULT)
-- ---------------------------------------------------------------------------
ALTER TABLE cities
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE zones
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE oauth_accounts
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE magic_login_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE dog_documents
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE membership_plans
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE walker_profiles
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE meeting_points
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE walk_recurrence_rules
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE waitlist_entries
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE walk_reports
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE walk_report_dog_notes
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE conversation_members
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE payouts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE webhook_events
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE content_pages
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE user_roles
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Triggers updated_at (idempotents)
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'cities', 'zones', 'oauth_accounts', 'profiles', 'dogs', 'dog_documents',
        'membership_plans', 'memberships', 'walker_profiles', 'meeting_points',
        'walks', 'walk_recurrence_rules', 'bookings', 'waitlist_entries',
        'attendance', 'walk_reports', 'walk_report_dog_notes', 'conversations',
        'conversation_members', 'messages', 'reviews', 'reports', 'payments',
        'payouts', 'users', 'settings', 'content_pages'
    ]
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_updated_at ON %I', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE PROCEDURE barkwalk_set_updated_at()',
            t, t
        );
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 2. accepted_sizes / accepted_energy : VARCHAR CSV → text[]
-- ---------------------------------------------------------------------------
ALTER TABLE walks ADD COLUMN IF NOT EXISTS accepted_sizes_arr TEXT[];
ALTER TABLE walks ADD COLUMN IF NOT EXISTS accepted_energy_arr TEXT[];

UPDATE walks
SET accepted_sizes_arr = ARRAY(
        SELECT trim(both FROM x)
        FROM unnest(string_to_array(accepted_sizes, ',')) AS x
        WHERE trim(both FROM x) <> ''
    )
WHERE accepted_sizes_arr IS NULL;

UPDATE walks
SET accepted_energy_arr = ARRAY(
        SELECT trim(both FROM x)
        FROM unnest(string_to_array(accepted_energy, ',')) AS x
        WHERE trim(both FROM x) <> ''
    )
WHERE accepted_energy_arr IS NULL;

ALTER TABLE walks ALTER COLUMN accepted_sizes_arr SET NOT NULL;
ALTER TABLE walks ALTER COLUMN accepted_energy_arr SET NOT NULL;

ALTER TABLE walks DROP COLUMN accepted_sizes;
ALTER TABLE walks DROP COLUMN accepted_energy;

ALTER TABLE walks RENAME COLUMN accepted_sizes_arr TO accepted_sizes;
ALTER TABLE walks RENAME COLUMN accepted_energy_arr TO accepted_energy;

-- ---------------------------------------------------------------------------
-- 3. CHECK — énumérations / domaines métier
-- ---------------------------------------------------------------------------
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_role_check;
ALTER TABLE user_roles
    ADD CONSTRAINT user_roles_role_check
    CHECK (role IN ('USER', 'MEMBER', 'COMMUNITY_ORG', 'WALKER_STAFF', 'WALKER_INDEP', 'ADMIN'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_sex_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_sex_check CHECK (sex IN ('MALE', 'FEMALE'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_size_category_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_size_category_check
    CHECK (size_category IN ('SMALL', 'MEDIUM', 'LARGE'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_energy_level_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_energy_level_check
    CHECK (energy_level IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_sociability_dogs_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_sociability_dogs_check
    CHECK (sociability_dogs IN ('GOOD', 'AVERAGE', 'FRAGILE', 'UNKNOWN'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_sociability_humans_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_sociability_humans_check
    CHECK (sociability_humans IN ('GOOD', 'AVERAGE', 'FRAGILE', 'UNKNOWN'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_danger_category_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_danger_category_check
    CHECK (danger_category IS NULL OR danger_category IN ('1', '2'));

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_dossier_status_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_dossier_status_check
    CHECK (dossier_status IN ('DRAFT', 'PENDING', 'VALIDATED', 'REFUSED'));

ALTER TABLE dog_documents DROP CONSTRAINT IF EXISTS dog_documents_doc_type_check;
ALTER TABLE dog_documents
    ADD CONSTRAINT dog_documents_doc_type_check
    CHECK (doc_type IN ('VACCINATION', 'INSURANCE', 'IDENTIFICATION', 'OTHER'));

ALTER TABLE dog_documents DROP CONSTRAINT IF EXISTS dog_documents_status_check;
ALTER TABLE dog_documents
    ADD CONSTRAINT dog_documents_status_check
    CHECK (status IN ('PENDING', 'VALIDATED', 'REFUSED', 'EXPIRED'));

ALTER TABLE walker_profiles DROP CONSTRAINT IF EXISTS walker_profiles_type_check;
ALTER TABLE walker_profiles
    ADD CONSTRAINT walker_profiles_type_check
    CHECK (walker_type IN ('STAFF', 'INDEP'));

ALTER TABLE walker_profiles DROP CONSTRAINT IF EXISTS walker_profiles_status_check;
ALTER TABLE walker_profiles
    ADD CONSTRAINT walker_profiles_status_check
    CHECK (status IN ('PENDING', 'VALIDATED', 'REFUSED', 'SUSPENDED'));

ALTER TABLE memberships DROP CONSTRAINT IF EXISTS memberships_status_check;
ALTER TABLE memberships
    ADD CONSTRAINT memberships_status_check
    CHECK (status IN ('ACTIVE', 'CANCELLED', 'PAST_DUE', 'INCOMPLETE', 'EXPIRED'));

ALTER TABLE membership_plans DROP CONSTRAINT IF EXISTS membership_plans_interval_check;
ALTER TABLE membership_plans
    ADD CONSTRAINT membership_plans_interval_check
    CHECK (interval_unit IN ('MONTH', 'YEAR', 'ONE_TIME'));

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_walk_type_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_walk_type_check
    CHECK (walk_type IN ('STAFF_GUIDED', 'COMMUNITY'));

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_status_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'));

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_capacity_positive;
ALTER TABLE walks
    ADD CONSTRAINT walks_capacity_positive CHECK (capacity > 0);

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_community_capacity_max;
ALTER TABLE walks
    ADD CONSTRAINT walks_community_capacity_max
    CHECK (walk_type <> 'COMMUNITY' OR capacity <= 8);

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_staff_requires_walker;
ALTER TABLE walks
    ADD CONSTRAINT walks_staff_requires_walker
    CHECK (walk_type <> 'STAFF_GUIDED' OR walker_id IS NOT NULL);

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_community_requires_organizer;
ALTER TABLE walks
    ADD CONSTRAINT walks_community_requires_organizer
    CHECK (walk_type <> 'COMMUNITY' OR organizer_user_id IS NOT NULL);

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_prices_non_negative;
ALTER TABLE walks
    ADD CONSTRAINT walks_prices_non_negative
    CHECK (
        price_cents >= 0
        AND (non_member_price_cents IS NULL OR non_member_price_cents >= 0)
    );

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_accepted_sizes_valid;
ALTER TABLE walks
    ADD CONSTRAINT walks_accepted_sizes_valid
    CHECK (
        cardinality(accepted_sizes) >= 1
        AND accepted_sizes <@ ARRAY['SMALL', 'MEDIUM', 'LARGE']::TEXT[]
    );

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_accepted_energy_valid;
ALTER TABLE walks
    ADD CONSTRAINT walks_accepted_energy_valid
    CHECK (
        cardinality(accepted_energy) >= 1
        AND accepted_energy <@ ARRAY['LOW', 'MEDIUM', 'HIGH']::TEXT[]
    );

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_min_sociability_check;
ALTER TABLE walks
    ADD CONSTRAINT walks_min_sociability_check
    CHECK (
        min_sociability_dogs IS NULL
        OR min_sociability_dogs IN ('GOOD', 'AVERAGE', 'FRAGILE', 'UNKNOWN')
    );

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
    CHECK (status IN (
        'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW', 'WAITLISTED'
    ));

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_price_non_negative;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_price_non_negative CHECK (price_cents >= 0);

ALTER TABLE waitlist_entries DROP CONSTRAINT IF EXISTS waitlist_entries_status_check;
ALTER TABLE waitlist_entries
    ADD CONSTRAINT waitlist_entries_status_check
    CHECK (status IN ('WAITING', 'OFFERED', 'ACCEPTED', 'EXPIRED', 'CANCELLED'));

ALTER TABLE attendance DROP CONSTRAINT IF EXISTS attendance_departure_status_check;
ALTER TABLE attendance
    ADD CONSTRAINT attendance_departure_status_check
    CHECK (departure_status IS NULL OR departure_status IN ('PRESENT', 'ABSENT', 'LATE'));

ALTER TABLE attendance DROP CONSTRAINT IF EXISTS attendance_return_status_check;
ALTER TABLE attendance
    ADD CONSTRAINT attendance_return_status_check
    CHECK (return_status IS NULL OR return_status IN ('PRESENT', 'ABSENT', 'EARLY'));

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_rating_check;
ALTER TABLE reviews
    ADD CONSTRAINT reviews_rating_check CHECK (rating BETWEEN 1 AND 5);

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_author_ne_target;
ALTER TABLE reviews
    ADD CONSTRAINT reviews_author_ne_target CHECK (author_id <> target_user_id);

ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_check;
ALTER TABLE reports
    ADD CONSTRAINT reports_status_check
    CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_amount_non_negative;
ALTER TABLE payments
    ADD CONSTRAINT payments_amount_non_negative CHECK (amount_cents >= 0);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_commission_non_negative;
ALTER TABLE payments
    ADD CONSTRAINT payments_commission_non_negative CHECK (commission_cents >= 0);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED', 'PARTIAL_REFUND'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_type_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_type_check
    CHECK (payment_type IN ('BOOKING', 'MEMBERSHIP', 'TIP', 'OTHER'));

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_amount_non_negative;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_amount_non_negative CHECK (amount_cents >= 0);

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_status_check;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_status_check
    CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED'));

ALTER TABLE membership_plans DROP CONSTRAINT IF EXISTS membership_plans_price_non_negative;
ALTER TABLE membership_plans
    ADD CONSTRAINT membership_plans_price_non_negative CHECK (price_cents >= 0);

ALTER TABLE user_blocks DROP CONSTRAINT IF EXISTS user_blocks_not_self;
ALTER TABLE user_blocks
    ADD CONSTRAINT user_blocks_not_self CHECK (blocker_id <> blocked_id);

ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_type_check;
ALTER TABLE conversations
    ADD CONSTRAINT conversations_type_check
    CHECK (conversation_type IN ('WALK', 'SUPPORT', 'DIRECT', 'MODERATION'));

-- ---------------------------------------------------------------------------
-- 4. Unicités réelles (index partiels quand nécessaire)
-- ---------------------------------------------------------------------------
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_walk_id_dog_id_key;
DROP INDEX IF EXISTS bookings_walk_id_dog_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_walk_dog_active
    ON bookings (walk_id, dog_id)
    WHERE status <> 'CANCELLED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_one_trial_per_user
    ON bookings (user_id)
    WHERE is_trial IS TRUE;

ALTER TABLE waitlist_entries DROP CONSTRAINT IF EXISTS waitlist_entries_walk_id_dog_id_key;
DROP INDEX IF EXISTS waitlist_entries_walk_id_dog_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_waitlist_walk_dog_active
    ON waitlist_entries (walk_id, dog_id)
    WHERE status IN ('WAITING', 'OFFERED');

-- WebhookEvent (provider, event_id) déjà UNIQUE en V1 — renommer pour clarté si besoin
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'webhook_events_provider_event_id_key'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'uq_webhook_events_provider_event'
    ) THEN
        CREATE UNIQUE INDEX uq_webhook_events_provider_event
            ON webhook_events (provider, event_id);
    END IF;
END $$;

-- Review / OAuth / ConversationMember / UserRole déjà couverts en V1 (PK ou UNIQUE)

-- ---------------------------------------------------------------------------
-- 5. Clés étrangères — comportements explicites
--    Principe : pas de CASCADE sur financier / audit ; CASCADE OK sur possession stricte.
-- ---------------------------------------------------------------------------

-- Bookings : ne jamais effacer une réservation via suppression user/dog/walk
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_walk_id_fkey;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_walk_id_fkey
    FOREIGN KEY (walk_id) REFERENCES walks(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_user_id_fkey;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_dog_id_fkey;
ALTER TABLE bookings
    ADD CONSTRAINT bookings_dog_id_fkey
    FOREIGN KEY (dog_id) REFERENCES dogs(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Payments / Payouts : immutabilité relationnelle
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_user_id_fkey;
ALTER TABLE payments
    ADD CONSTRAINT payments_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_booking_id_fkey;
ALTER TABLE payments
    ADD CONSTRAINT payments_booking_id_fkey
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_membership_id_fkey;
ALTER TABLE payments
    ADD CONSTRAINT payments_membership_id_fkey
    FOREIGN KEY (membership_id) REFERENCES memberships(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_walker_id_fkey;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_walker_id_fkey
    FOREIGN KEY (walker_id) REFERENCES walker_profiles(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE payouts DROP CONSTRAINT IF EXISTS payouts_payment_id_fkey;
ALTER TABLE payouts
    ADD CONSTRAINT payouts_payment_id_fkey
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Audit : acteur peut être anonymisé plus tard (SET NULL), jamais de cascade delete audit
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_actor_id_fkey;
ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Documents chien : possession stricte
ALTER TABLE dog_documents DROP CONSTRAINT IF EXISTS dog_documents_dog_id_fkey;
ALTER TABLE dog_documents
    ADD CONSTRAINT dog_documents_dog_id_fkey
    FOREIGN KEY (dog_id) REFERENCES dogs(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- Walks → meeting point / city / walker
ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_city_id_fkey;
ALTER TABLE walks
    ADD CONSTRAINT walks_city_id_fkey
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_meeting_point_id_fkey;
ALTER TABLE walks
    ADD CONSTRAINT walks_meeting_point_id_fkey
    FOREIGN KEY (meeting_point_id) REFERENCES meeting_points(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_walker_id_fkey;
ALTER TABLE walks
    ADD CONSTRAINT walks_walker_id_fkey
    FOREIGN KEY (walker_id) REFERENCES walker_profiles(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_organizer_user_id_fkey;
ALTER TABLE walks
    ADD CONSTRAINT walks_organizer_user_id_fkey
    FOREIGN KEY (organizer_user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Reviews / reports
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_walk_id_fkey;
ALTER TABLE reviews
    ADD CONSTRAINT reviews_walk_id_fkey
    FOREIGN KEY (walk_id) REFERENCES walks(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_reporter_id_fkey;
ALTER TABLE reports
    ADD CONSTRAINT reports_reporter_id_fkey
    FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_decided_by_fkey;
ALTER TABLE reports
    ADD CONSTRAINT reports_decided_by_fkey
    FOREIGN KEY (decided_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Attendance lié à booking
ALTER TABLE attendance DROP CONSTRAINT IF EXISTS attendance_booking_id_fkey;
ALTER TABLE attendance
    ADD CONSTRAINT attendance_booking_id_fkey
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Messagerie : CASCADE OK (conteneur conversation)
ALTER TABLE conversation_members DROP CONSTRAINT IF EXISTS conversation_members_conversation_id_fkey;
ALTER TABLE conversation_members
    ADD CONSTRAINT conversation_members_conversation_id_fkey
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_conversation_id_fkey;
ALTER TABLE messages
    ADD CONSTRAINT messages_conversation_id_fkey
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- ---------------------------------------------------------------------------
-- 6. Index requêtes critiques
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_walks_catalogue
    ON walks (status, starts_at)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_walks_city_status_starts
    ON walks (city_id, status, starts_at);

CREATE INDEX IF NOT EXISTS idx_bookings_walk_status
    ON bookings (walk_id, status);

CREATE INDEX IF NOT EXISTS idx_bookings_pending_payment_created
    ON bookings (created_at)
    WHERE status = 'PENDING_PAYMENT';

CREATE INDEX IF NOT EXISTS idx_waitlist_walk_status_position
    ON waitlist_entries (walk_id, status, position);

CREATE INDEX IF NOT EXISTS idx_reviews_target_created
    ON reviews (target_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payments_booking
    ON payments (booking_id)
    WHERE booking_id IS NOT NULL;
