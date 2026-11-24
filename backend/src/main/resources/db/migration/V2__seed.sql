-- Seed: cities France (échantillon), settings, plans, contenus

ALTER TABLE cities DROP CONSTRAINT IF EXISTS cities_insee_code_key;

INSERT INTO cities (insee_code, postal_code, name, department, region, lat, lng) VALUES
('69123', '69001', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.764043, 4.835659),
('69123', '69002', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.746, 4.827),
('69123', '69003', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.759, 4.863),
('69123', '69006', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.769, 4.850),
('69123', '69007', 'Lyon', '69', 'Auvergne-Rhône-Alpes', 45.732, 4.840),
('75056', '75001', 'Paris', '75', 'Île-de-France', 48.8606, 2.3376),
('75056', '75011', 'Paris', '75', 'Île-de-France', 48.8575, 2.3800),
('75056', '75015', 'Paris', '75', 'Île-de-France', 48.8412, 2.3000),
('13055', '13001', 'Marseille', '13', 'Provence-Alpes-Côte d''Azur', 43.2965, 5.3698),
('13055', '13008', 'Marseille', '13', 'Provence-Alpes-Côte d''Azur', 43.2560, 5.3800),
('31555', '31000', 'Toulouse', '31', 'Occitanie', 43.6045, 1.4440),
('33063', '33000', 'Bordeaux', '33', 'Nouvelle-Aquitaine', 44.8378, -0.5792),
('44109', '44000', 'Nantes', '44', 'Pays de la Loire', 47.2184, -1.5536),
('67482', '67000', 'Strasbourg', '67', 'Grand Est', 48.5734, 7.7521),
('59350', '59000', 'Lille', '59', 'Hauts-de-France', 50.6292, 3.0573),
('21231', '21000', 'Dijon', '21', 'Bourgogne-Franche-Comté', 47.3220, 5.0415),
('34172', '34000', 'Montpellier', '34', 'Occitanie', 43.6108, 3.8767),
('51454', '51100', 'Reims', '51', 'Grand Est', 49.2583, 4.0317),
('38185', '38000', 'Grenoble', '38', 'Auvergne-Rhône-Alpes', 45.1885, 5.7245),
('69266', '69100', 'Villeurbanne', '69', 'Auvergne-Rhône-Alpes', 45.7660, 4.8795),
('69290', '69300', 'Caluire-et-Cuire', '69', 'Auvergne-Rhône-Alpes', 45.7960, 4.8440),
('69259', '69200', 'Vénissieux', '69', 'Auvergne-Rhône-Alpes', 45.6970, 4.8850)
ON CONFLICT (insee_code, postal_code) DO NOTHING;

INSERT INTO zones (city_id, name)
SELECT c.id, z.name
FROM cities c
CROSS JOIN (VALUES ('Presqu''île'), ('Croix-Rousse'), ('Guillotière'), ('Part-Dieu'), ('Confluence')) AS z(name)
WHERE c.postal_code = '69001'
ON CONFLICT (city_id, name) DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
('cancellation_free_hours', '24', 'R4 Annulation gratuite (heures)'),
('pending_payment_minutes', '15', 'R6 Libération place non payée'),
('waitlist_offer_hours', '2', 'R7 Délai offre liste d''attente'),
('community_walks_threshold', '3', 'R8 Seuil balades pour organisateur'),
('community_capacity_max', '8', 'R11 Capacité max communautaire'),
('indep_commission_percent', '15', 'R12 Commission indépendants %'),
('review_window_days', '14', 'R13 Fenêtre avis'),
('org_min_rating', '3', 'R10 Note mini organisateur'),
('org_max_reports', '2', 'R10 Signalements max')
ON CONFLICT (key) DO NOTHING;

INSERT INTO membership_plans (code, name, description, price_cents, interval_unit, active) VALUES
('MONTHLY', 'Adhésion mensuelle', 'Accès aux balades encadrées salariés incluses', 2900, 'MONTH', TRUE),
('YEARLY', 'Adhésion annuelle', '12 mois, économie vs mensuel', 29000, 'YEAR', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO content_pages (slug, title, body, version) VALUES
('cgu', 'Conditions générales d''utilisation', 'Texte CGU V1 à valider.', '1.0'),
('privacy', 'Politique de confidentialité', 'Texte confidentialité V1.', '1.0'),
('charter', 'Charte communautaire', 'Texte charte V1.', '1.0'),
('faq', 'FAQ', 'Questions fréquentes V1.', '1.0')
ON CONFLICT (slug) DO NOTHING;

-- Comptes démo : mots de passe fixés au démarrage par DemoDataInitializer (Admin123!)
-- Ici uniquement structures géo / tarifs / contenus.
