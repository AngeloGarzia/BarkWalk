-- Étape 3 — comptes / RGPD

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='public_id') THEN
    RAISE EXCEPTION 'FAIL users.public_id missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='refresh_tokens' AND column_name='family_id') THEN
    RAISE EXCEPTION 'FAIL refresh_tokens.family_id missing';
  END IF;
  RAISE NOTICE 'PASS public_id and family_id';
END $$;

-- Anonymisation
DO $$
DECLARE
  v_id bigint;
  v_email text;
BEGIN
  INSERT INTO users (email, password_hash, email_verified, enabled, deleted)
  VALUES ('to-anonymize-' || gen_random_uuid() || '@example.com', 'hash', TRUE, TRUE, FALSE)
  RETURNING id INTO v_id;
  INSERT INTO profiles (user_id, first_name, last_name, community_org_status)
  VALUES (v_id, 'Temp', 'User', 'NONE');
  PERFORM barkwalk_anonymize_user(v_id);
  SELECT email INTO v_email FROM users WHERE id = v_id;
  IF v_email NOT LIKE 'deleted+%@anonymized.invalid' THEN
    RAISE EXCEPTION 'FAIL anonymize email %', v_email;
  END IF;
  IF (SELECT first_name FROM profiles WHERE user_id = v_id) <> 'Membre' THEN
    RAISE EXCEPTION 'FAIL anonymize profile';
  END IF;
  RAISE NOTICE 'PASS anonymize_user';
END $$;

-- Vue export
DO $$
BEGIN
  PERFORM 1 FROM v_user_personal_data_export LIMIT 1;
  RAISE NOTICE 'PASS v_user_personal_data_export';
END $$;

-- storage_key trop court refusé
DO $$
DECLARE v_dog bigint;
BEGIN
  SELECT id INTO v_dog FROM dogs LIMIT 1;
  BEGIN
    INSERT INTO dog_documents (dog_id, doc_type, storage_key, status)
    VALUES (v_dog, 'OTHER', 'short', 'PENDING');
    RAISE EXCEPTION 'FAIL short storage_key accepted';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'PASS short storage_key rejected';
  END;
END $$;

SELECT 'STEP3_SQL_CHECKS_DONE' AS result;
