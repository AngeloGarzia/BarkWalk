-- R2 / R16 — dossier chien : statut EXPIRED + alerte admin sur résas futures.

ALTER TABLE dogs DROP CONSTRAINT IF EXISTS dogs_dossier_status_check;
ALTER TABLE dogs
    ADD CONSTRAINT dogs_dossier_status_check
    CHECK (dossier_status IN ('DRAFT', 'PENDING', 'VALIDATED', 'REFUSED', 'EXPIRED'));

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS admin_alert BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_bookings_admin_alert
    ON bookings (id)
    WHERE admin_alert IS TRUE;

COMMENT ON COLUMN bookings.admin_alert IS 'R16 : réservation future à traiter après expiration dossier chien';
