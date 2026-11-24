-- Bark & Walk V1 schema

CREATE TABLE cities (
    id              BIGSERIAL PRIMARY KEY,
    insee_code      VARCHAR(5)  NOT NULL,
    postal_code     VARCHAR(5)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    department      VARCHAR(3)  NOT NULL,
    region          VARCHAR(100) NOT NULL,
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (insee_code, postal_code)
);
CREATE INDEX idx_cities_postal ON cities (postal_code);
CREATE INDEX idx_cities_name ON cities (LOWER(name));

CREATE TABLE zones (
    id          BIGSERIAL PRIMARY KEY,
    city_id     BIGINT NOT NULL REFERENCES cities(id),
    name        VARCHAR(120) NOT NULL,
    UNIQUE (city_id, name)
);

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255),
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE oauth_accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(40) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE magic_login_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE consent_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_type   VARCHAR(40) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    accepted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    first_name      VARCHAR(80) NOT NULL,
    last_name       VARCHAR(80) NOT NULL,
    phone           VARCHAR(30),
    bio             VARCHAR(500),
    photo_key       VARCHAR(255),
    city_id         BIGINT REFERENCES cities(id),
    zone_id         BIGINT REFERENCES zones(id),
    community_org_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    community_org_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    completed_walks_count INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE dogs (
    id                  BIGSERIAL PRIMARY KEY,
    owner_id            BIGINT NOT NULL REFERENCES users(id),
    name                VARCHAR(100) NOT NULL,
    breed               VARCHAR(100) NOT NULL,
    birth_date          DATE,
    sex                 VARCHAR(10) NOT NULL,
    sterilized          BOOLEAN NOT NULL DEFAULT FALSE,
    weight_kg           NUMERIC(5,2),
    size_category       VARCHAR(20) NOT NULL,
    identification_number VARCHAR(50),
    photo_key           VARCHAR(255),
    energy_level        VARCHAR(20) NOT NULL,
    sociability_dogs    VARCHAR(20) NOT NULL,
    sociability_humans  VARCHAR(20) NOT NULL,
    recall_level        VARCHAR(20),
    leash_pulling       VARCHAR(20),
    attention_notes     TEXT,
    danger_category     VARCHAR(5),
    dossier_status      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    refusal_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dogs_owner ON dogs (owner_id);

CREATE TABLE dog_documents (
    id              BIGSERIAL PRIMARY KEY,
    dog_id          BIGINT NOT NULL REFERENCES dogs(id) ON DELETE CASCADE,
    doc_type        VARCHAR(40) NOT NULL,
    storage_key     VARCHAR(255) NOT NULL,
    expires_at      DATE,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE membership_plans (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(40) NOT NULL UNIQUE,
    name                VARCHAR(120) NOT NULL,
    description         TEXT,
    price_cents         INTEGER NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    interval_unit       VARCHAR(20) NOT NULL,
    stripe_price_id     VARCHAR(120),
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE memberships (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id),
    plan_id                 BIGINT NOT NULL REFERENCES membership_plans(id),
    status                  VARCHAR(30) NOT NULL,
    current_period_start    TIMESTAMPTZ,
    current_period_end      TIMESTAMPTZ,
    stripe_subscription_id  VARCHAR(120),
    stripe_customer_id      VARCHAR(120),
    cancel_at_period_end    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_memberships_user ON memberships (user_id);

CREATE TABLE walker_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id),
    walker_type             VARCHAR(20) NOT NULL,
    siret                   VARCHAR(14),
    diploma_key             VARCHAR(255),
    rc_pro_key              VARCHAR(255),
    rc_pro_expires_at       DATE,
    stripe_connect_account  VARCHAR(120),
    charter_signed_at       TIMESTAMPTZ,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    bio                     VARCHAR(500),
    average_rating          NUMERIC(3,2),
    ratings_count           INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE meeting_points (
    id          BIGSERIAL PRIMARY KEY,
    city_id     BIGINT NOT NULL REFERENCES cities(id),
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE walks (
    id                      BIGSERIAL PRIMARY KEY,
    city_id                 BIGINT NOT NULL REFERENCES cities(id),
    meeting_point_id        BIGINT NOT NULL REFERENCES meeting_points(id),
    walk_type               VARCHAR(30) NOT NULL,
    title                   VARCHAR(160) NOT NULL,
    description             TEXT,
    starts_at               TIMESTAMPTZ NOT NULL,
    duration_minutes        INTEGER NOT NULL,
    capacity                INTEGER NOT NULL,
    price_cents             INTEGER NOT NULL DEFAULT 0,
    non_member_price_cents  INTEGER,
    walker_id               BIGINT REFERENCES walker_profiles(id),
    organizer_user_id       BIGINT REFERENCES users(id),
    status                  VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    accepted_sizes          VARCHAR(80) NOT NULL,
    accepted_energy         VARCHAR(80) NOT NULL,
    min_sociability_dogs    VARCHAR(20),
    route_hint              TEXT,
    community_disclaimer_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_walks_city_starts ON walks (city_id, starts_at);
CREATE INDEX idx_walks_type ON walks (walk_type);

CREATE TABLE walk_recurrence_rules (
    id              BIGSERIAL PRIMARY KEY,
    template_walk_id BIGINT NOT NULL REFERENCES walks(id),
    cron_expression VARCHAR(80) NOT NULL,
    until_date      DATE,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    walk_id             BIGINT NOT NULL REFERENCES walks(id),
    user_id             BIGINT NOT NULL REFERENCES users(id),
    dog_id              BIGINT NOT NULL REFERENCES dogs(id),
    status              VARCHAR(30) NOT NULL,
    payment_required    BOOLEAN NOT NULL DEFAULT FALSE,
    price_cents         INTEGER NOT NULL DEFAULT 0,
    community_ack       BOOLEAN NOT NULL DEFAULT FALSE,
    is_trial            BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_session_id   VARCHAR(120),
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (walk_id, dog_id)
);
CREATE INDEX idx_bookings_user ON bookings (user_id);
CREATE INDEX idx_bookings_walk ON bookings (walk_id);

CREATE TABLE waitlist_entries (
    id              BIGSERIAL PRIMARY KEY,
    walk_id         BIGINT NOT NULL REFERENCES walks(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    dog_id          BIGINT NOT NULL REFERENCES dogs(id),
    position        INTEGER NOT NULL,
    offered_at      TIMESTAMPTZ,
    offer_expires_at TIMESTAMPTZ,
    status          VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (walk_id, dog_id)
);

CREATE TABLE attendance (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    departure_status VARCHAR(20),
    departure_at    TIMESTAMPTZ,
    return_status   VARCHAR(20),
    return_at       TIMESTAMPTZ
);

CREATE TABLE walk_reports (
    id              BIGSERIAL PRIMARY KEY,
    walk_id         BIGINT NOT NULL UNIQUE REFERENCES walks(id),
    author_id       BIGINT NOT NULL REFERENCES users(id),
    actual_duration_minutes INTEGER,
    route_done      TEXT,
    incident_type   VARCHAR(40),
    incident_description TEXT,
    incident_severity VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE walk_report_dog_notes (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES walk_reports(id) ON DELETE CASCADE,
    dog_id          BIGINT NOT NULL REFERENCES dogs(id),
    behavior_score  INTEGER,
    comment         TEXT,
    UNIQUE (report_id, dog_id)
);

CREATE TABLE conversations (
    id              BIGSERIAL PRIMARY KEY,
    conversation_type VARCHAR(40) NOT NULL,
    walk_id         BIGINT REFERENCES walks(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMPTZ
);

CREATE TABLE conversation_members (
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       BIGINT NOT NULL REFERENCES users(id),
    body            TEXT,
    photo_key       VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ
);

CREATE TABLE user_blocks (
    blocker_id      BIGINT NOT NULL REFERENCES users(id),
    blocked_id      BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    walk_id         BIGINT NOT NULL REFERENCES walks(id),
    author_id       BIGINT NOT NULL REFERENCES users(id),
    target_user_id  BIGINT NOT NULL REFERENCES users(id),
    rating          INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    reply           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (walk_id, author_id, target_user_id)
);

CREATE TABLE reports (
    id              BIGSERIAL PRIMARY KEY,
    reporter_id     BIGINT NOT NULL REFERENCES users(id),
    target_type     VARCHAR(40) NOT NULL,
    target_id       BIGINT NOT NULL,
    reason          TEXT NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    decision        VARCHAR(40),
    decided_by      BIGINT REFERENCES users(id),
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    booking_id          BIGINT REFERENCES bookings(id),
    membership_id       BIGINT REFERENCES memberships(id),
    amount_cents        INTEGER NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    payment_type        VARCHAR(40) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    stripe_payment_intent VARCHAR(120),
    commission_cents    INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payouts (
    id                  BIGSERIAL PRIMARY KEY,
    walker_id           BIGINT NOT NULL REFERENCES walker_profiles(id),
    payment_id          BIGINT NOT NULL REFERENCES payments(id),
    amount_cents        INTEGER NOT NULL,
    status              VARCHAR(30) NOT NULL,
    stripe_transfer_id  VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(40) NOT NULL,
    event_id        VARCHAR(120) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, event_id)
);

CREATE TABLE settings (
    key             VARCHAR(80) PRIMARY KEY,
    value           TEXT NOT NULL,
    description     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE content_pages (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(80) NOT NULL UNIQUE,
    title           VARCHAR(160) NOT NULL,
    body            TEXT NOT NULL,
    version         VARCHAR(20) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT REFERENCES users(id),
    action          VARCHAR(80) NOT NULL,
    entity_type     VARCHAR(60),
    entity_id       BIGINT,
    details         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_created ON audit_log (created_at DESC);
