-- Unicité différée du slot photo (réindexage transactionnel possible).
ALTER TABLE profile_photos
    DROP CONSTRAINT IF EXISTS uq_profile_photos_user_sort;
ALTER TABLE profile_photos
    ADD CONSTRAINT uq_profile_photos_user_sort UNIQUE (user_id, sort_order)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE dog_photos
    DROP CONSTRAINT IF EXISTS uq_dog_photos_dog_sort;
ALTER TABLE dog_photos
    ADD CONSTRAINT uq_dog_photos_dog_sort UNIQUE (dog_id, sort_order)
    DEFERRABLE INITIALLY DEFERRED;

-- Anonymisation : purger aussi les lignes photos (fichiers disque côté app afterCommit).
CREATE OR REPLACE FUNCTION barkwalk_anonymize_user(p_user_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_public UUID;
BEGIN
    SELECT public_id INTO v_public FROM users WHERE id = p_user_id;
    IF v_public IS NULL THEN
        RAISE EXCEPTION 'user not found';
    END IF;

    DELETE FROM profile_photos WHERE user_id = p_user_id;
    DELETE FROM dog_photos
    WHERE dog_id IN (SELECT id FROM dogs WHERE owner_id = p_user_id);

    UPDATE users
    SET email = 'deleted+' || replace(v_public::text, '-', '') || '@anonymized.invalid',
        password_hash = NULL,
        enabled = FALSE,
        deleted = TRUE,
        deleted_at = NOW(),
        anonymized_at = NOW(),
        updated_at = NOW()
    WHERE id = p_user_id;

    UPDATE profiles
    SET first_name = 'Membre',
        last_name = 'supprimé',
        phone = NULL,
        bio = NULL,
        photo_key = NULL,
        updated_at = NOW()
    WHERE user_id = p_user_id;

    UPDATE refresh_tokens SET revoked = TRUE WHERE user_id = p_user_id AND revoked IS FALSE;
END;
$$;
