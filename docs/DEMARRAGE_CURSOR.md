# Démarrer Bark & Walk dans Cursor (A → Z)

Guide pas à pas pour lancer l’appli **depuis Cursor** sur Windows (PowerShell).

---

## 1. Prérequis (à installer une fois)

| Outil | Version attendue | Vérification |
|-------|------------------|--------------|
| [Cursor](https://cursor.com) | — | IDE déjà ouvert |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | récent | `docker --version` |
| [Java JDK](https://adoptium.net/) | **21** | `java -version` |
| [Maven](https://maven.apache.org/) | 3.9+ | `mvn -version` |
| [Node.js](https://nodejs.org/) (LTS) | 20+ | `node -v` / `npm -v` |
| Git | — | `git --version` |

Docker Desktop doit être **démarré** (icône baleine dans la barre des tâches) avant `docker compose`.

---

## 2. Ouvrir le projet dans Cursor

1. **File → Open Folder…**
2. Choisir le dossier du repo, par ex. `C:\Users\<toi>\BarkWalk`
3. Attendre l’indexation (barre de statut en bas)

Si le repo n’est pas encore cloné :

```powershell
cd C:\Users\<toi>
git clone https://github.com/AngeloGarzia/BarkWalk.git
```

Puis ouvrir `BarkWalk` dans Cursor.

---

## 3. Terminal Cursor

1. **Terminal → New Terminal** (ou `` Ctrl+` ``)
2. Vérifier que le shell est **PowerShell** et que le chemin commence par le dossier du projet :

```powershell
pwd
# → ...\BarkWalk
```

> Sous PowerShell, **ne pas** enchaîner avec `&&` (ancienne syntaxe).  
> Lancer les commandes **une par une**, ou utiliser `;` / plusieurs terminaux.

Conseil : ouvrir **3 terminaux** (onglets) :

| Terminal | Rôle |
|----------|------|
| A | Docker (infra) |
| B | Backend Spring Boot |
| C | Frontend Angular |

---

## 4. Fichier d’environnement (une fois)

À la racine du projet :

```powershell
Copy-Item .env.example .env
```

Le fichier `.env` reste **local** (gitignoré). Tu peux laisser les valeurs d’exemple pour le développement.

---

## 5. Dépendances frontend (une fois, ou après `git pull`)

```powershell
cd frontend
npm install
cd ..
```

---

## 6. Démarrer l’infra (Docker)

**Terminal A** — racine du projet :

```powershell
docker compose up -d
```

Services lancés :

| Service | Port hôte | Usage |
|---------|-----------|--------|
| PostgreSQL 17 | **5434** → 5432 | Base `barkwalk` / user `barkwalk` / mdp `barkwalk` |
| MinIO | 9000 (API), **9001** (console) | Stockage docs |
| Mailhog | 1025 (SMTP), **8025** (UI) | E-mails de test |

Vérifier :

```powershell
docker compose ps
```

Les 3 containers principaux doivent être `running` / `healthy`.

---

## 7. Démarrer le backend

**Terminal B** — racine du projet :

```powershell
mvn -pl backend spring-boot:run
```

Attendre une ligne du type :

```text
Started BarkWalkApplication in … seconds
```

- API : http://localhost:8080  
- Swagger : http://localhost:8080/swagger-ui.html  

Flyway applique les migrations (`V1` → `V6`) au démarrage. Les comptes démo sont créés/mis à jour automatiquement.

Laisser ce terminal **ouvert** (processus long).

---

## 8. Démarrer le frontend

**Terminal C** :

```powershell
cd frontend
npm start
```

Attendre :

```text
Local:   http://localhost:4200/
```

Si le CLI Angular demande les analytics Google : répondre **`N`** puis Entrée.

Ouvrir dans le navigateur : **http://localhost:4200**

Le proxy (`proxy.conf.json`) redirige `/api` vers `http://localhost:8080`.

---

## 9. Se connecter (comptes démo)

Mot de passe commun : **`Admin123!`** (avec le point d’exclamation)

| E-mail | Rôle |
|--------|------|
| `admin@barkwalk.fr` | ADMIN |
| `alice@example.com` | MEMBER (+ organisateur communautaire) |
| `walker@barkwalk.fr` | WALKER_STAFF |

Sur l’écran Connexion, des raccourcis peuvent préremplir ces comptes.

---

## 10. URLs utiles

| URL | Description |
|-----|-------------|
| http://localhost:4200 | Application (PWA) |
| http://localhost:8080/swagger-ui.html | Documentation API |
| http://localhost:8025 | Mailhog (boîte mail locale) |
| http://localhost:9001 | MinIO (`barkwalk` / `barkwalksecret`) |

---

## 11. Arrêter proprement

### Frontend / backend (terminaux B et C)

Dans chaque terminal actif : **Ctrl+C**

### Libérer un port bloqué (ex. 4200 ou 8080)

```powershell
Get-NetTCPConnection -LocalPort 4200 -State Listen |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ -Force }
```

Remplacer `4200` par `8080` pour le backend.

### Docker (optionnel)

```powershell
docker compose stop
# ou tout supprimer (données Postgres conservées dans le volume) :
# docker compose down
```

---

## 12. Relancer après un redémarrage PC

Ordre recommandé :

1. Démarrer **Docker Desktop**
2. Terminal A : `docker compose up -d`
3. Terminal B : `mvn -pl backend spring-boot:run`
4. Terminal C : `cd frontend` puis `npm start`
5. Ouvrir http://localhost:4200

---

## 13. Dépannage rapide

| Problème | Piste |
|----------|--------|
| `docker` introuvable / erreur daemon | Lancer Docker Desktop et réessayer |
| Backend refuse la DB | Vérifier `docker compose ps` et le port **5434** |
| Port 4200 / 8080 déjà pris | Tuer le process (section 11) puis relancer |
| Login « identifiants incorrects » | Utiliser exactement `Admin123!` |
| Carte MapLibre / worker | Relancer `npm start` après `git pull` ; assets worker dans `angular.json` |
| `&&` ne marche pas | PowerShell : une commande par ligne |
| `npm install` long / erreurs | Node LTS, supprimer `frontend/node_modules` puis `npm install` |
| Backend ne démarre pas | Java **21** obligatoire (`java -version`) |

### Vérifier l’API sans le front

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"email":"admin@barkwalk.fr","password":"Admin123!"}'
```

Une réponse avec `accessToken` confirme que le back + la DB sont OK.

---

## 14. Aide Cursor (Agent)

Tu peux demander à l’agent Cursor, par exemple :

- « Redémarre le backend »
- « Ferme le port 4200 »
- « Relance toute l’appli »

Fournir le chemin du projet et, si besoin, coller les erreurs du terminal.

---

## Récap en 5 commandes

```powershell
# Racine BarkWalk
Copy-Item .env.example .env   # une fois
docker compose up -d

# Terminal B
mvn -pl backend spring-boot:run

# Terminal C
cd frontend
npm install                   # une fois
npm start
```

Puis : **http://localhost:4200**
