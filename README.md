# Bark & Walk V1

Application full-stack : **Angular 21 PWA** + **Spring Boot 4.2** + **PostgreSQL**, multi-villes France (nom ou code postal).

## Structure

```
BarkWalk/
├── pom.xml
├── docker-compose.yml      # Postgres + MinIO + Mailhog
├── backend/                # API Spring Boot (JWT, CRUD métier)
├── frontend/               # Angular (auth, catalogue, chiens, admin…)
└── .env.example
```

## Stack

| Couche | Techno |
|--------|--------|
| Backend | Spring Boot **4.2.0**, Java **21** |
| Starters | WebMVC, JPA, Security, Validation, Actuator, Flyway, Mail, Cache, OAuth2 client |
| Extra | JWT (jjwt), Stripe, AWS S3 SDK, Bucket4j, springdoc 3.2.1 |
| DB | PostgreSQL 17 + Flyway |
| Front | Angular **21**, MapLibre, date-fns |
| Infra locale | MinIO (docs), Mailhog (emails) |

## Démarrage

```bash
docker compose up -d
./mvnw -pl backend spring-boot:run     # Windows : .\mvnw.cmd -pl backend spring-boot:run
cd frontend && npm start
```

> Postgres est exposé sur le port **5434** (5432/5433 souvent déjà pris sur cette machine).

- Front : http://localhost:4200  
- API / Swagger : http://localhost:8080/swagger-ui.html  
- Mailhog : http://localhost:8025  
- MinIO console : http://localhost:9001 (`barkwalk` / `barkwalksecret`)

### Comptes démo (mot de passe `Admin123!`)

| Email | Rôles |
|-------|--------|
| `admin@barkwalk.fr` | ADMIN |
| `alice@example.com` | MEMBER + organisateur communautaire |
| `walker@barkwalk.fr` | WALKER_STAFF |

## Sécurité

- JWT access (15 min) + refresh opaque (hash SHA-256 en base)
- RBAC cumulable : USER, MEMBER, COMMUNITY_ORG, WALKER_STAFF, WALKER_INDEP, ADMIN
- Rate limit Bucket4j sur login / register / magic-link
- Documents via URLs signées (stub MinIO)
- Webhooks Stripe idempotents (`webhook_events`)
- Audit log admin

## API principales

| Domaine | Endpoints |
|---------|-----------|
| Auth | `POST /api/auth/register\|login\|refresh\|logout\|magic-link` |
| Villes | `GET /api/cities?q=`, `GET /api/cities/{id}/zones` |
| Profil | `GET\|PUT /api/me/profile` |
| Chiens | `CRUD /api/dogs`, submit, documents ; admin validate/refuse |
| Balades | `GET /api/walks`, `POST /api/walks` (staff/community) |
| Résas | `GET\|POST /api/bookings`, cancel (verrou pessimiste) |
| Adhésion | `GET /api/memberships/plans`, `/me` |
| Pointage / CR | `/api/attendance/*` |
| Messagerie | `/api/messaging/*` |
| Avis / signalements | `/api/reviews`, `/api/reports` |
| Encadrants | `/api/walkers/apply`, admin validate |
| Paiements | `POST /api/payments/webhook` |
| Admin | `/api/admin/dashboard`, settings, finance |

## Multi-villes

Recherche publique par **nom** ou **code postal** (`GET /api/cities?q=69001` ou `q=Lyon`).  
Seed : Lyon (plusieurs CP), Paris, Marseille, Toulouse, Bordeaux, Nantes, etc.
