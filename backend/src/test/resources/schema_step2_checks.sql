-- Étape 2 — tests passant / bloquant

-- R6 : payment_expires_at présent sur PENDING
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='bookings' AND column_name='payment_expires_at'
  ) THEN
    RAISE EXCEPTION 'FAIL missing payment_expires_at';
  END IF;
  RAISE NOTICE 'PASS payment_expires_at column';
END $$;

-- R4 : CANCELLED_LATE exige comptage double
DO $$
DECLARE
  v_walk bigint; v_user bigint; v_dog bigint;
BEGIN
  SELECT id INTO v_walk FROM walks LIMIT 1;
  SELECT id INTO v_user FROM users WHERE email='alice@example.com';
  SELECT id INTO v_dog FROM dogs WHERE owner_id=v_user LIMIT 1;
  BEGIN
    INSERT INTO bookings (walk_id, user_id, dog_id, status, price_cents, is_trial,
                          counts_toward_org_threshold, counts_toward_membership_quota)
    VALUES (v_walk, v_user, v_dog, 'CANCELLED_LATE', 0, FALSE, FALSE, FALSE);
    RAISE EXCEPTION 'FAIL expected late_cancel_counts_check';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS late_cancel_without_counts rejected';
  END;
END $$;

-- R13 : avis hors fenêtre 14j refusé
DO $$
DECLARE
  v_walk bigint; v_author bigint; v_target bigint; v_old_start timestamptz;
BEGIN
  SELECT id, starts_at INTO v_walk, v_old_start FROM walks ORDER BY id LIMIT 1;
  SELECT id INTO v_author FROM users WHERE email='alice@example.com';
  SELECT id INTO v_target FROM users WHERE email='walker@barkwalk.fr';
  UPDATE walks SET starts_at = NOW() - INTERVAL '20 days' WHERE id = v_walk;
  BEGIN
    INSERT INTO reviews (walk_id, author_id, target_user_id, rating, created_at)
    VALUES (v_walk, v_author, v_target, 5, NOW());
    UPDATE walks SET starts_at = v_old_start WHERE id = v_walk;
    RAISE EXCEPTION 'FAIL expected review window';
  EXCEPTION WHEN check_violation THEN
    UPDATE walks SET starts_at = v_old_start WHERE id = v_walk;
    RAISE NOTICE 'PASS review_outside_window rejected';
  END;
END $$;

-- WalkerDocument table exists
DO $$
BEGIN
  PERFORM 1 FROM walker_documents LIMIT 0;
  RAISE NOTICE 'PASS walker_documents exists';
END $$;

-- community_org_status unique field
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='profiles' AND column_name='community_org_enabled'
  ) THEN
    RAISE EXCEPTION 'FAIL legacy community_org_enabled still present';
  END IF;
  RAISE NOTICE 'PASS community_org_status only';
END $$;

-- Incidents table
DO $$
BEGIN
  PERFORM 1 FROM incidents LIMIT 0;
  RAISE NOTICE 'PASS incidents exists';
END $$;

-- Email templates
DO $$
BEGIN
  IF (SELECT count(*) FROM email_templates) < 1 THEN
    RAISE EXCEPTION 'FAIL email_templates empty';
  END IF;
  RAISE NOTICE 'PASS email_templates seeded';
END $$;

-- disclaimer column removed
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='walks' AND column_name='community_disclaimer_required'
  ) THEN
    RAISE EXCEPTION 'FAIL community_disclaimer_required still present';
  END IF;
  RAISE NOTICE 'PASS disclaimer column removed';
END $$;

SELECT 'STEP2_SQL_CHECKS_DONE' AS result;
