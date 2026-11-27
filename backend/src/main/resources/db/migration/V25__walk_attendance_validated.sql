-- Validation des présents par l'organisateur après l'heure de balade.
ALTER TABLE walks
    ADD COLUMN IF NOT EXISTS attendance_validated_at TIMESTAMPTZ;

COMMENT ON COLUMN walks.attendance_validated_at IS
    'Horodatage de validation du pointage (présents/absents) par orga / encadrant / admin.';
