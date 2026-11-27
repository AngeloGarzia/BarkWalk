# Bark & Walk — Règles métier mises en place

Document de synthèse des règles appliquées dans l’application (backend + UI).  
Sources : implémentation serveur, paramètres `settings`, migrations Flyway, recette R1→R16.  
Date de synthèse : **2026-11-26**.

Les droits et contrôles métier sont appliqués **côté serveur**. Les valeurs « paramétrables » vivent dans la table `settings` (admin), pas en dur dans le code.

---

## 1. Vocabulaire

### Profils / rôles

| Rôle | Signification |
|------|----------------|
| Visiteur | Non connecté |
| `USER` | Inscrit (non adhérent) |
| `MEMBER` | Adhérent |
| `COMMUNITY_ORG` | Organisateur de balades communautaires |
| `WALKER_STAFF` | Encadrant salarié |
| `WALKER_INDEP` | Encadrant indépendant |
| `ADMIN` | Administrateur |

Un compte peut cumuler plusieurs rôles.

### Types de balade

| Code | Libellé |
|------|---------|
| `STAFF_GUIDED` | Encadrée (salarié) |
| `INDEP_GUIDED` | Encadrée (indépendant) |
| `COMMUNITY` | Communautaire |

### Statuts utiles

- **Réservation** : `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED_FREE`, `CANCELLED_LATE`, `COMPLETED`, `NO_SHOW`, `WAITLISTED`
- **Liste d’attente** : `WAITING`, `OFFERED`, `ACCEPTED`, `EXPIRED`, `CANCELLED`
- **Dossier chien** : `DRAFT`, `PENDING`, `VALIDATED`, `REFUSED`, `EXPIRED`
- **Balade** : `DRAFT`, `PUBLISHED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` (+ soft-archive via `archived_at`)

---

## 2. Paramètres métier (configurables)

| Clé | Défaut | Règles liées |
|-----|--------|--------------|
| `cancellation_free_hours` | 24 | R4 |
| `pending_payment_minutes` | 15 | R6 |
| `waitlist_offer_hours` | 2 | R7 |
| `community_walks_threshold` | 3 | R8 |
| `community_capacity_max` | 8 | R11 |
| `indep_commission_percent` | 15 | R12 |
| `review_window_days` | 14 | R13 (avis) |
| `org_min_rating` | 3 | R10 |
| `org_max_reports` | 2 | R10 |
| `org_min_reviews` | 5 | R10 |
| `doc_reminder_j30_days` | 30 | R16 |
| `doc_reminder_j7_days` | 7 | R16 |
| `limit_messages_per_hour` | 60 | R13 (messagerie) |

---

## 3. Matrice R1 → R16

### R1 — Essai gratuit

- **1 essai gratuit par compte** (`trial = true`), unique en base.
- Uniquement sur balade **`STAFF_GUIDED`**.
- Refusé sur communautaire / indépendant, et si l’essai a déjà été utilisé.

### R2 — Dossier chien obligatoire pour réserver

- Réservation possible seulement si le dossier est **`VALIDATED`**.
- Documents requis : **vaccination** + **assurance RC**, validés et **non expirés**.
- Validation admin bloquée tant que ces docs ne sont pas en ordre.

### R3 — Compatibilité chien ↔ balade

- Contrôle **taille**, **énergie**, **sociabilité minimale**.
- En cas d’échec : motif précis renvoyé (pas un simple « incompatible »).
- Capacité : places occupées = `CONFIRMED` + `PENDING_PAYMENT` ; verrou pessimiste sur la balade à la réservation.

### R4 — Annulation par le participant

- Jusqu’à **X heures** avant le départ (`cancellation_free_hours`) → `CANCELLED_FREE` (gratuit).
- Au-delà → `CANCELLED_LATE` : **pas de remboursement**, balade **comptée** pour le seuil org **et** le quota adhésion.
- Déclenche l’offre suivante en liste d’attente (R7).

### R5 — Annulation par le staff / organisateur / admin

> **Prod** : le remboursement Stripe n’est pas encore branché (`stub-full-refund` en base). À remplacer avant mise en production.


- Annulation staff → statut gratuit pour le participant + **remboursement intégral** + notification email.
- Réservé organisateur / encadrant assigné / admin.

### R6 — Place non payée

- Si paiement requis → statut `PENDING_PAYMENT` + `payment_expires_at` = maintenant + **Y minutes**.
- Job planifié : expire les résas dépassées, **libère la place**, propose la waitlist (R7).

### R7 — Liste d’attente

- Balade pleine → inscription en `WAITING` (position FIFO).
- Place libérée → offre au premier (`OFFERED`) avec délai **Z heures**.
- Expiration de l’offre → cascade au suivant.
- Acceptation → réservation confirmée.
- L’inscription waitlist est **commitée** même si l’API répond 409 (pas de rollback transactionnel).

### R8 — Pointage / balades terminées / éligibilité organisateur

- Après l’**heure de fin** de balade (`startsAt` + durée), l’organisateur (ou encadrant / admin) doit **valider les présents** (case à cocher par inscrit).
- Présent → réservation `COMPLETED` + incrément `completed_walks_count` ; absent → `NO_SHOW`.
- Tant que le pointage n’est pas validé (`attendance_validated_at` vide) : **rappel clignotant** sur le menu **Balades**.
- À partir du **seuil** configuré → statut organisateur `ELIGIBLE` (si non révoqué).
- Publication communautaire : admin, ou rôle `COMMUNITY_ORG`, ou profil `ACTIVE` / `ELIGIBLE` (seuil atteint) et non révoqué.

### R9 — Balade communautaire

- **Disclaimer** obligatoire à la réservation (`communityAck`).
- **Adhésion** (`MEMBER` ou `ADMIN`) obligatoire pour s’inscrire.
- Capacité plafonnée (R11).

### R10 — Retrait d’organisateur

Évaluation automatique (après balade / jobs) :

- Signalements **validés** ≥ `org_max_reports`, **ou**
- Note moyenne &lt; `org_min_rating` dès que ≥ `org_min_reviews` avis.

→ Statut `REVOKED`, retrait du rôle `COMMUNITY_ORG`, audit.

### R11 — Capacité communautaire

- Capacité d’une balade `COMMUNITY` ≤ `community_capacity_max` (défaut 8).
- Balades encadrées : capacité libre (admin / indépendant), sous réserve des autres contrôles.

### R12 — Commission indépendants

- Sur paiement d’une balade `INDEP_GUIDED` : commission % plateforme (`indep_commission_percent`), reste à l’indépendant.
- Calcul borné / contrôlé en base et en service.

### R13 — Avis et messagerie

#### Avis (notation balade 1–5 ★)

- Réservé aux **participants** ayant une réservation éligible.
- Ouverture **à partir du lendemain** de la balade, dans la fenêtre `review_window_days` (défaut 14 j).
- Un avis par utilisateur et par balade.
- Met à jour la **note moyenne du créateur** (affichage organisateur).

#### Messagerie

- Types : `WALK` (groupe balade), `DIRECT`, `SUPPORT`, `MODERATION`.
- Fil `WALK` créé avec la balade (orga + encadrant) ; membres ajoutés à la confirmation de résa.
- **DM** : non-adhérent **ne peut pas** écrire en privé à un adhérent.
- **Blocage** bilatéral : interdiction d’échanger.
- **Rate limit** : max messages / heure / utilisateur (`limit_messages_per_hour`).
- Archive des fils `WALK` **J+7** après fin de balade (job).

### R14 — Adhésion

- Cycle : `ACTIVE` / `PAST_DUE` / `CANCELLED` / `EXPIRED`.
- Droits adhérent actifs dès confirmation du **premier paiement** d’adhésion.
- Rôle `MEMBER` synchronisé côté app avec l’état d’adhésion.

### R15 — Encadrants indépendants (publication)

Pour publier une balade indépendante, profil validé + :

- SIRET, diplôme, RC pro, **charte signée** ;
- RC pro **non expirée**.

Sinon publication refusée.

### R16 — Documents expirés & rappels

- Document requis passé date → doc / dossier **`EXPIRED`** ; réservation future bloquée.
- Rappels email **J-30** et **J-7** (paramétrables) pour chiens (et logique walker associée).
- Indépendant : doc / RC expirée bloque aussi la publication (lien R15).

---

## 4. Règles complémentaires (hors numérotation R1–R16)

### 4.1 Catalogue & visibilité des balades

- Catalogue public : balades `PUBLISHED`, non archivées, à venir, filtrées par **ville**.
- **Archives** : utilisateurs connectés — filtrées par ville (même nom de commune) ; **admin** : toutes les archives.
- **Admin — vue « Toutes les balades »** : région → département, filtres de statut (Publiée, En cours, Terminée, Annulée, Brouillon, Archivée), filtre ville conservé ; tuiles repliées par défaut.
- **Organisateur** : section **Mes balades à venir** sur le catalogue (créations non archivées, à venir / en cours).

### 4.2 Cycle de vie balade

- `start` / `complete` : organisateur, encadrant assigné ou admin.
- **Soft-archive** (`archived_at`) : retire du catalogue sans suppression physique.
- Suppression physique refusée s’il existe des réservations (archiver à la place).
- **Recyclage** : copie d’une balade passée (archivée / terminée / annulée) vers une nouvelle édition future ; photos & notes restent sur la lignée source (`recycled_from_walk_id`).

### 4.3 Parcours GPS & difficulté

- Parcours optionnel : GeoJSON **LineString**, 2–50 points `[lng, lat]`.
- Badge UI « parcours GPS » si la LineString est renseignée.
- Difficulté **1–10** calculée (durée, dénivelé, terrain) ; barre visuelle sur les tuiles.
- Suivi live GPS côté organisateur / encadrant ; **JWT court** conservé (15 min) avec refresh proactif + buffer local de la trace pendant la balade.

### 4.4 Photos

- Profil / chien : 1–3 photos, URLs signées HMAC, contrôle magic bytes.
- Balade : **PROMO** (tuiles catalogue) et **GALLERY** (pendant / après balade en cours ou terminée).
- Galerie : organisateur ou participants autorisés.

### 4.5 Participants (pseudos)

- Liste des inscrits (pseudo via `displayName`) visible **uniquement** pour :
  - **admin** (toutes les balades),
  - **organisateur** de la balade,
  - **encadrant assigné**.
- Un **participant simple ne voit pas** les autres participants (champ absent / null côté API).

### 4.6 Confidentialité & identité

- Affichage public prioritaire : **pseudo** ; sinon prénom + nom ; sinon libellé anonymisé.
- Téléphone : soi-même, admin, ou staff de la balade concernée.
- Anonymisation compte (RGPD) : procédure DB + purge photos associées.
- Jetons : access **15 min**, refresh **14 j**, rotation au refresh ; empreinte SHA-256 seule en base.

### 4.7 Paiements & notifications

- Stripe (webhook) ; commission indep (R12).
- Emails : Brevo API ou SMTP (Mailhog en local) — R5, R7, R16, rappel veille de balade.
- Jobs planifiés : expiration paiement (R6), waitlist (R7), adhésion (R14), docs (R16), archive messages, rappel J-1.

### 4.8 Catégories chien « danger » 1 / 2

- Balades dédiées via `allows_danger_categories` : **réservées à l’admin** à la création.

---

## 5. Automatismes planifiés (rappel)

| Job | Effet |
|-----|--------|
| Expiration `PENDING_PAYMENT` | Libère places + waitlist |
| Expiration offres waitlist | Cascade R7 |
| Expiration / rappels docs | R16 |
| Adhésions | Transitions R14 |
| Archive fils WALK | J+7 après fin |
| Rappel veille | Email inscrits `CONFIRMED` |

---

## 6. Où retrouver le détail technique

| Document / zone | Contenu |
|-----------------|--------|
| `docs/REGLES_METIER_PROMPT.md` | Prompt d’implémentation pas à pas |
| `docs/RECETTE_R1_R16.md` | Matrice tests automatisés R1→R16 |
| `docs/RECETTE_ETAPE5.md` | Schéma, sécurité, migrations |
| `docs/mcd-barkwalk.dbml` | Modèle de données |
| `backend/.../BusinessSettingsService.java` | Défauts des paramètres |
| Services `Booking*`, `Waitlist*`, `Messaging*`, `Walk*`, `DogDossier*`, `CommunityOrg*`, etc. | Règles exécutées |

---

## 7. Recette rapide

```powershell
cd backend
mvn "-Dtest=DogDossierServiceTest,DogWalkCompatibilityServiceTest,BookingServiceTest,WaitlistServiceTest,PaymentServiceTest,CommissionCalculatorTest,MembershipServiceTest,CommunityOrgServiceTest,ReviewServiceTest,MessagingServiceTest,WalkerPublishEligibilityTest,WalkServiceTest,BusinessJobsSchedulerTest,BusinessSettingsServiceTest,PrivacyServiceTest" test
```

---

*Ce fichier décrit l’état des règles telles qu’implémentées dans le dépôt à la date de synthèse. En cas d’écart, le code serveur et les tests font foi.*
