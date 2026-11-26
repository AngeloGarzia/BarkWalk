-- Alignement Hibernate : difficulty_level en INTEGER (pas SMALLINT).
ALTER TABLE walks
    ALTER COLUMN difficulty_level TYPE INTEGER
    USING difficulty_level::integer;
