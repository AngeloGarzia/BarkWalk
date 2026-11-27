# Qualité : Checkstyle, SpotBugs, CodeQL, SonarCloud

## Outils

| Outil | Périmètre | Où |
|-------|-----------|-----|
| **Checkstyle** | Style / conventions Java | `mvn verify` (backend) |
| **SpotBugs** (+ FindSecBugs) | Bugs & sécu bytecode | `mvn verify` (backend) |
| **JaCoCo** | Couverture Java | `mvn verify` (backend) |
| **CodeQL** | Sécu + qualité Java / TS | workflow `.github/workflows/codeql.yml` |
| **SonarCloud** | Qualité globale + dette | workflow `.github/workflows/sonarcloud.yml` |

Configs :

- Checkstyle : `backend/config/checkstyle/`
- SpotBugs excludes : `backend/config/spotbugs/exclude.xml`
- CodeQL : `.github/codeql/codeql-config.yml`
- Sonar : `pom.xml` (props) + `sonar-project.properties`

Script utilitaire : `scripts/fix_checkstyle_imports.py` (nettoie unused imports à partir de `checkstyle-result.xml`).

## Local

```powershell
# Tout (tests + checkstyle + spotbugs + jacoco)
mvn -pl backend -am verify

# Isolé
mvn -pl backend checkstyle:check
mvn -pl backend spotbugs:check
```

Rapports :

- Checkstyle : `backend/target/checkstyle-result.xml`
- SpotBugs : `backend/target/spotbugsXml.xml`
- JaCoCo : `backend/target/site/jacoco/index.html`

## SonarCloud (une fois)

1. Créer un compte sur [sonarcloud.io](https://sonarcloud.io) (GitHub).
2. Importer l’orga / le dépôt `AngeloGarzia/BarkWalk`.
3. Noter **organization** et **project key** (souvent `angelogarzia` / `AngeloGarzia_BarkWalk`).
4. Dans GitHub → *Settings* → *Secrets and variables* → *Actions* :
   - Secret **`SONAR_TOKEN`** (token SonarCloud).
   - Variables optionnelles : `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` si différentes des défauts.
5. Pousser sur `master` ou ouvrir une PR : le workflow **SonarCloud** s’exécute si `SONAR_TOKEN` est défini.

Sans `SONAR_TOKEN`, le job Sonar est **ignoré** (CI backend/frontend et CodeQL restent actifs).

## CodeQL

Le workflow analyse **Java** (build Maven manuel) et **JavaScript/TypeScript**.  
Résultats : onglet *Security* → *Code scanning alerts* du dépôt GitHub.

Sur un dépôt **privé**, CodeQL nécessite GitHub Advanced Security (ou un plan qui l’inclut). Sur un dépôt **public**, c’est inclus.
