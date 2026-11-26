# Bark & Walk — Recette étapes 1–5 (schéma & sécurité)

## Décision R4 (annulation tardive « comptée »)

**Retenue : les deux** — une annulation `CANCELLED_LATE` force  
`counts_toward_org_threshold = true` **et** `counts_toward_membership_quota = true`  
(contrainte `bookings_late_cancel_counts_check`).

## Migrations Flyway

| Version | Objet |
|---------|--------|
| V1 | Schéma initial |
| V2 | Seed |
| V3 | Intégrité (CHECK, unicités, FK, index, text[]) |
| V4 | Règles métier R1–R16 (modèle) |
| V5 | Comptes, RGPD, UUID publics |
| V6 | Accès, paiements immuables, audit append-only, rôles DB |
| V7 | Tables `profile_photos` / `dog_photos` (1–3 slots) |
| V8–V11 | Settings restants, INDEP_GUIDED, etc. (voir migrations) |
| V12 | UNIQUE différé `(user_id, sort_order)` / `(dog_id, sort_order)` + anonymize purge photos |

## Matrice R1 → R16

| Règle | Garantie schéma / migration | Test | Statut |
|-------|-----------------------------|------|--------|
| R1 Essai unique | `uq_bookings_one_trial_per_user` (V3) | step1 SQL / IT | OK |
| R2 Compatibilité chien | App + tailles/énergie typées (V3) | BookingService | Partiel (app) |
| R3 Capacité | Verrou `findByIdForUpdate` + index actif (V6) | Concurrence app | Partiel |
| R4 Annulation | Statuts FREE/LATE + flags comptage (V4) | step2 late_cancel | OK modèle |
| R5 Membres communauté | App (rôle MEMBER) | — | Partiel |
| R6 Paiement 15 min | `payment_expires_at` + index (V4) | step2 column | OK |
| R7 Waitlist | Unicité partielle active (V3) | — | OK modèle |
| R8 Seuil 3 balades | `community_org_status` + compteur (V4) | Attendance → ELIGIBLE | OK |
| R9 Disclaimer | Dérivé `walk_type=COMMUNITY` (V4 drop colonne) | step2 | OK |
| R10 Org révoqué | Statut `REVOKED` + historique (V4) | — | OK modèle |
| R11 Capacité com. ≤8 | CHECK `walks_community_capacity_max` (V3) | step1 | OK |
| R12 Commission | CHECK commission ≤ montant + borne settings (V4/V6) | step4 | OK |
| R13 Avis 14 j | Trigger `barkwalk_review_within_window` (V4) | step2 | OK |
| R14 Double avis | UNIQUE (walk, author, target) (V1/V3) | — | OK |
| R15 Docs walker | Table `walker_documents` (V4) | step2 | OK |
| R16 Rappels docs | `reminder_j30/j7_sent_at` dog+walker (V4) | — | OK modèle |

## Checklist sécurité

- [x] Jetons refresh / magic : hash stocké (`token_hash`)
- [x] Famille refresh + colonnes rotation / réutilisation (V5) — logique app rotation à brancher
- [x] Documents : clés ≥ 16 car., pas d’URL publique en schéma
- [x] Droits par ligne : à appliquer côté service (schéma prêt UUID publics)
- [x] Audit append-only (trigger V6) + REVOKE UPDATE/DELETE rôle app
- [x] Webhooks : unicité (provider, event_id) + signature_valid / payload_hash (V6)
- [x] Anonymisation RGPD : `barkwalk_anonymize_user` + vue export (V5)
- [x] Rôles DB `barkwalk_app` / `_migrator` / `_readonly` (V6, sans secret en repo)
- [x] Pas de secret / MDP rôle dans Git
- [x] Identifiants publics UUID (users, dogs, walks, bookings, …)
- [ ] Rate-limit / verrouillage login : colonnes prêtes, filtre app à renforcer
- [x] Liens médias signés HMAC (`/api/media/...?exp=&sig=`) ; S3/MinIO à brancher avant prod
- [ ] Confirmation paiement uniquement via webhook : à verrouiller côté service (confirmPayment browser encore présent)

## Risques / points ouverts

1. **Catégories danger 1 et 2** : CHECK `danger_category IN ('1','2')` — confirmer libellés métier.
2. **Statut fiscal commission** : non modélisé (hors V1 données).
3. **Rôles DB** : créés `NOLOGIN` ; ops doit `ALTER ROLE … LOGIN PASSWORD` hors dépôt et basculer le datasource app.
4. **Testcontainers** : IT étape 1 skippés si Docker API Desktop HS ; validation via scripts SQL `schema_step*_checks.sql`.
5. **RLS PostgreSQL** : non activé ; autorisation encore majoritairement applicative.
6. **Duplication MEMBER / Membership** : vérité adhésion = `memberships` ; rôle `MEMBER` encore synchronisé en app (nettoyage progressif).

## Comment rejouer les tests

```powershell
Get-Content -Raw backend\src\test\resources\schema_step1_checks.sql | docker exec -i barkwalk-postgres psql -U barkwalk -d barkwalk -v ON_ERROR_STOP=1
Get-Content -Raw backend\src\test\resources\schema_step2_checks.sql | docker exec -i barkwalk-postgres psql -U barkwalk -d barkwalk -v ON_ERROR_STOP=1
Get-Content -Raw backend\src\test\resources\schema_step3_checks.sql | docker exec -i barkwalk-postgres psql -U barkwalk -d barkwalk -v ON_ERROR_STOP=1
Get-Content -Raw backend\src\test\resources\schema_step4_checks.sql | docker exec -i barkwalk-postgres psql -U barkwalk -d barkwalk -v ON_ERROR_STOP=1
```

DBML à jour : `docs/mcd-barkwalk.dbml` (importer sur https://dbdiagram.io).
