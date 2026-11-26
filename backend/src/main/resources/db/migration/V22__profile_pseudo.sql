-- Pseudo public (affiché sur les tuiles de balades créées / participées).
ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS pseudo VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_profiles_pseudo_ci
    ON profiles (lower(pseudo))
    WHERE pseudo IS NOT NULL AND length(trim(pseudo)) > 0;

COMMENT ON COLUMN profiles.pseudo IS 'Pseudo public optionnel ; prioritaire sur prénom/nom pour l''affichage';

-- Pseudos démo (idempotent).
UPDATE profiles SET pseudo = 'AdminBW', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'admin@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'AliceCanine', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'alice@example.com') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'SamPromeneur', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'walker@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'JulesPattes', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'user@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'LeaBalades', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'member@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'CamilleOrg', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'org@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
UPDATE profiles SET pseudo = 'InesIndep', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'indep@barkwalk.fr') AND (pseudo IS NULL OR pseudo = '');
