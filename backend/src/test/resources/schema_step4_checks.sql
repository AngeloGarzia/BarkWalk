-- Étape 4 — audit append-only, payments immutables, settings bounds

-- Audit UPDATE refusé
DO $$
DECLARE v_id bigint;
BEGIN
  INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details)
  VALUES (NULL, 'TEST_STEP4', 'TEST', NULL, 'x')
  RETURNING id INTO v_id;
  BEGIN
    UPDATE audit_log SET action = 'HACK' WHERE id = v_id;
    RAISE EXCEPTION 'FAIL audit update allowed';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS audit_log update forbidden';
  END;
END $$;

-- Payment montant immutable
DO $$
DECLARE v_id bigint; v_user bigint;
BEGIN
  SELECT id INTO v_user FROM users LIMIT 1;
  INSERT INTO payments (user_id, amount_cents, payment_type, status, commission_cents)
  VALUES (v_user, 1000, 'BOOKING', 'SUCCEEDED', 100)
  RETURNING id INTO v_id;
  BEGIN
    UPDATE payments SET amount_cents = 1 WHERE id = v_id;
    RAISE EXCEPTION 'FAIL payment amount mutate allowed';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS payment amount immutable';
  END;
  -- refund OK
  UPDATE payments SET refund_amount_cents = 200, refund_reason = 'test', status = 'PARTIAL_REFUND'
  WHERE id = v_id;
  RAISE NOTICE 'PASS payment refund columns writable';
END $$;

-- Setting commission absurde
DO $$
BEGIN
  BEGIN
    INSERT INTO settings (key, value, description) VALUES ('indep_commission_percent_test', '15', 't')
    ON CONFLICT DO NOTHING;
    UPDATE settings SET value = '150' WHERE key = 'indep_commission_percent';
    -- restore
    UPDATE settings SET value = '15' WHERE key = 'indep_commission_percent';
    RAISE EXCEPTION 'FAIL commission 150 allowed';
  EXCEPTION WHEN check_violation THEN
    UPDATE settings SET value = '15' WHERE key = 'indep_commission_percent';
    RAISE NOTICE 'PASS commission bound';
  END;
END $$;

-- Rôles DB existent
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='barkwalk_app') THEN
    RAISE EXCEPTION 'FAIL role barkwalk_app missing';
  END IF;
  RAISE NOTICE 'PASS db roles';
END $$;

SELECT 'STEP4_SQL_CHECKS_DONE' AS result;
