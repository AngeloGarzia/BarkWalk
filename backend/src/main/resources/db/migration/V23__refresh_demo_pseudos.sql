-- Rafraîchit les pseudos démo (canins / balades).
UPDATE profiles SET pseudo = 'GardienDuParc', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'admin@barkwalk.fr');
UPDATE profiles SET pseudo = 'FlairMartin', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'alice@example.com');
UPDATE profiles SET pseudo = 'CapLaisse', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'walker@barkwalk.fr');
UPDATE profiles SET pseudo = 'PatteDouce', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'user@barkwalk.fr');
UPDATE profiles SET pseudo = 'NalaEtLea', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'member@barkwalk.fr');
UPDATE profiles SET pseudo = 'BergesCamille', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'org@barkwalk.fr');
UPDATE profiles SET pseudo = 'CanicrossInes', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'indep@barkwalk.fr');
