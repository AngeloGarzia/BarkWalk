-- Complète les paramètres métier de l'étape 1 (rappels docs + seuil avis R10).

INSERT INTO settings (key, value, description) VALUES
('doc_reminder_j30_days', '30', 'R16 Rappel expiration document J-30'),
('doc_reminder_j7_days', '7', 'R16 Rappel expiration document J-7'),
('org_min_reviews', '5', 'R10 Nombre mini d''avis pour appliquer la note moyenne')
ON CONFLICT (key) DO NOTHING;

-- Harmonise les descriptions des clés cœur si déjà présentes
UPDATE settings SET description = 'R4 Annulation gratuite (heures)'
WHERE key = 'cancellation_free_hours' AND (description IS NULL OR description = '');
UPDATE settings SET description = 'R6 Libération place non payée (minutes)'
WHERE key = 'pending_payment_minutes';
UPDATE settings SET description = 'R7 Délai offre liste d''attente (heures)'
WHERE key = 'waitlist_offer_hours';
UPDATE settings SET description = 'R8 Seuil balades terminées pour organisateur'
WHERE key = 'community_walks_threshold';
UPDATE settings SET description = 'R11 Capacité max balade communautaire'
WHERE key = 'community_capacity_max';
UPDATE settings SET description = 'R12 Commission indépendants (%) — valeur provisoire'
WHERE key = 'indep_commission_percent';
UPDATE settings SET description = 'R13 Fenêtre pour laisser un avis (jours)'
WHERE key = 'review_window_days';
UPDATE settings SET description = 'R10 Note moyenne mini organisateur (/5)'
WHERE key = 'org_min_rating';
UPDATE settings SET description = 'R10 Signalements validés max avant retrait'
WHERE key = 'org_max_reports';
