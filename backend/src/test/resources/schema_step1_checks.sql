-- Vérifications Étape 1 (passant + bloquant) — exécuter via psql

-- Cas passant : staff walk OK
DO $$
DECLARE
  v_city bigint; v_mp bigint; v_walker bigint; v_walk bigint;
BEGIN
  SELECT id INTO v_city FROM cities WHERE postal_code='69001' LIMIT 1;
  SELECT id INTO v_mp FROM meeting_points WHERE city_id=v_city LIMIT 1;
  SELECT id INTO v_walker FROM walker_profiles LIMIT 1;
  INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
  VALUES (v_city, v_mp, 'STAFF_GUIDED', 'IT pass staff', NOW() + interval '5 days', 60, 6, 0, v_walker, 'PUBLISHED', ARRAY['LARGE'], ARRAY['HIGH'])
  RETURNING id INTO v_walk;
  RAISE NOTICE 'PASS staff_walk id=%', v_walk;
  DELETE FROM walks WHERE id = v_walk;
END $$;

-- Cas bloquant : community sans organisateur
DO $$
DECLARE
  v_city bigint; v_mp bigint;
BEGIN
  SELECT id INTO v_city FROM cities WHERE postal_code='69001' LIMIT 1;
  SELECT id INTO v_mp FROM meeting_points WHERE city_id=v_city LIMIT 1;
  BEGIN
    INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, status, accepted_sizes, accepted_energy)
    VALUES (v_city, v_mp, 'COMMUNITY', 'IT fail community', NOW() + interval '5 days', 45, 4, 0, 'PUBLISHED', ARRAY['SMALL'], ARRAY['LOW']);
    RAISE EXCEPTION 'FAIL expected constraint walks_community_requires_organizer';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS community_without_organizer rejected';
  END;
END $$;

-- Cas bloquant : capacité communautaire > 8
DO $$
DECLARE
  v_city bigint; v_mp bigint; v_user bigint;
BEGIN
  SELECT id INTO v_city FROM cities WHERE postal_code='69001' LIMIT 1;
  SELECT id INTO v_mp FROM meeting_points WHERE city_id=v_city LIMIT 1;
  SELECT id INTO v_user FROM users LIMIT 1;
  BEGIN
    INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, organizer_user_id, status, accepted_sizes, accepted_energy)
    VALUES (v_city, v_mp, 'COMMUNITY', 'IT fail cap', NOW() + interval '5 days', 45, 9, 0, v_user, 'PUBLISHED', ARRAY['SMALL'], ARRAY['LOW']);
    RAISE EXCEPTION 'FAIL expected walks_community_capacity_max';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS community_capacity_gt_8 rejected';
  END;
END $$;

-- Cas bloquant : taille invalide
DO $$
DECLARE
  v_city bigint; v_mp bigint; v_walker bigint;
BEGIN
  SELECT id INTO v_city FROM cities WHERE postal_code='69001' LIMIT 1;
  SELECT id INTO v_mp FROM meeting_points WHERE city_id=v_city LIMIT 1;
  SELECT id INTO v_walker FROM walker_profiles LIMIT 1;
  BEGIN
    INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
    VALUES (v_city, v_mp, 'STAFF_GUIDED', 'IT fail size', NOW() + interval '5 days', 60, 6, 0, v_walker, 'PUBLISHED', ARRAY['TINY'], ARRAY['HIGH']);
    RAISE EXCEPTION 'FAIL expected walks_accepted_sizes_valid';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS invalid_size rejected';
  END;
END $$;

-- Cas bloquant : self-review
DO $$
DECLARE
  v_walk bigint; v_user bigint;
BEGIN
  SELECT id INTO v_walk FROM walks LIMIT 1;
  SELECT id INTO v_user FROM users LIMIT 1;
  BEGIN
    INSERT INTO reviews (walk_id, author_id, target_user_id, rating) VALUES (v_walk, v_user, v_user, 5);
    RAISE EXCEPTION 'FAIL expected reviews_author_ne_target';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS self_review rejected';
  END;
END $$;

-- Cas bloquant : montant paiement négatif
DO $$
DECLARE v_user bigint;
BEGIN
  SELECT id INTO v_user FROM users LIMIT 1;
  BEGIN
    INSERT INTO payments (user_id, amount_cents, payment_type, status) VALUES (v_user, -1, 'BOOKING', 'PENDING');
    RAISE EXCEPTION 'FAIL expected payments_amount_non_negative';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS negative_payment rejected';
  END;
END $$;

-- Cas bloquant : user_block self
DO $$
DECLARE v_user bigint;
BEGIN
  SELECT id INTO v_user FROM users LIMIT 1;
  BEGIN
    INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (v_user, v_user);
    RAISE EXCEPTION 'FAIL expected user_blocks_not_self';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS self_block rejected';
  END;
END $$;

-- Unicité essai : 2e trial bloqué
DO $$
DECLARE
  v_city bigint; v_mp bigint; v_walker bigint; v_user bigint; v_dog bigint;
  v_walk1 bigint; v_walk2 bigint;
BEGIN
  SELECT id INTO v_city FROM cities WHERE postal_code='69001' LIMIT 1;
  SELECT id INTO v_mp FROM meeting_points WHERE city_id=v_city LIMIT 1;
  SELECT id INTO v_walker FROM walker_profiles LIMIT 1;
  SELECT id INTO v_user FROM users WHERE email = 'alice@example.com';
  SELECT id INTO v_dog FROM dogs WHERE owner_id = v_user LIMIT 1;

  INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
  VALUES (v_city, v_mp, 'STAFF_GUIDED', 'IT trial 1', NOW() + interval '6 days', 60, 6, 0, v_walker, 'PUBLISHED', ARRAY['LARGE'], ARRAY['HIGH'])
  RETURNING id INTO v_walk1;
  INSERT INTO walks (city_id, meeting_point_id, walk_type, title, starts_at, duration_minutes, capacity, price_cents, walker_id, status, accepted_sizes, accepted_energy)
  VALUES (v_city, v_mp, 'STAFF_GUIDED', 'IT trial 2', NOW() + interval '7 days', 60, 6, 0, v_walker, 'PUBLISHED', ARRAY['LARGE'], ARRAY['HIGH'])
  RETURNING id INTO v_walk2;

  INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents, is_trial)
  VALUES (v_walk1, v_user, v_dog, 'CONFIRMED', 0, TRUE);

  BEGIN
    INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents, is_trial)
    VALUES (v_walk2, v_user, v_dog, 'CONFIRMED', 0, TRUE);
    RAISE EXCEPTION 'FAIL expected uq_bookings_one_trial_per_user';
  EXCEPTION WHEN unique_violation THEN
    RAISE NOTICE 'PASS second_trial rejected';
  END;

  DELETE FROM bookings WHERE walk_id IN (v_walk1, v_walk2);
  DELETE FROM walks WHERE id IN (v_walk1, v_walk2);
END $$;

SELECT 'STEP1_SQL_CHECKS_DONE' AS result;
