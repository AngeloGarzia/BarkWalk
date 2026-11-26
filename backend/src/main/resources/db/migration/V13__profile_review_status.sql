-- Revue admin des fiches profil (identité / ville / téléphone).
ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(30) NOT NULL DEFAULT 'VALIDATED',
    ADD COLUMN IF NOT EXISTS review_refusal_reason VARCHAR(500);

ALTER TABLE profiles
    DROP CONSTRAINT IF EXISTS chk_profiles_review_status;

ALTER TABLE profiles
    ADD CONSTRAINT chk_profiles_review_status
    CHECK (review_status IN ('DRAFT', 'PENDING', 'VALIDATED', 'REFUSED'));

CREATE INDEX IF NOT EXISTS idx_profiles_review_status ON profiles (review_status);
