-- Doublons démo (ex. Bellecour sur deux CP Lyon) : une seule balade active par titre.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY lower(title) ORDER BY id) AS rn
    FROM walks
    WHERE title IN (
        'Balade communautaire Bellecour',
        'Balade matinale Tête d''Or',
        'Balade Tuileries',
        'Buttes-Chaumont canin',
        'Balade Borély',
        'Toulouse Jardin des Plantes',
        'Balade Jardin Public Bordeaux',
        'Nantes Île de Versailles',
        'Orangerie Strasbourg',
        'Balade Citadelle Lille',
        'Montpellier centre',
        'Grenoble Paul Mistral',
        'Descente Croix-Rousse (passée)'
    )
)
UPDATE walks w
SET status = 'CANCELLED',
    cancellation_reason = COALESCE(cancellation_reason, 'demo_duplicate'),
    archived_at = COALESCE(w.archived_at, NOW()),
    updated_at = NOW()
FROM ranked r
WHERE w.id = r.id
  AND r.rn > 1
  AND (w.archived_at IS NULL OR w.status = 'PUBLISHED');
