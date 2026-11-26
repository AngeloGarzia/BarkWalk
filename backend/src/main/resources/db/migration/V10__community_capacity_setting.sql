-- R11 : le plafond communautaire est piloté par settings (community_capacity_max),
-- le CHECK DB ne garde qu'une borne de sécurité haute.
ALTER TABLE walks DROP CONSTRAINT IF EXISTS walks_community_capacity_max;
ALTER TABLE walks
    ADD CONSTRAINT walks_community_capacity_max
    CHECK (walk_type <> 'COMMUNITY' OR capacity <= 100);

COMMENT ON CONSTRAINT walks_community_capacity_max ON walks IS
    'Borne de sécurité ; le plafond métier vient de community_capacity_max';
