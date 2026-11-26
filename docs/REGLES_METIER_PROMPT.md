# Règles métier — prompt d’implémentation Bark & Walk (V1)

Source : `regles _metiers_prompt.docx` (Bureau / `docs/regles_metiers_prompt.docx`).

## Rôle

Tu es un développeur senior. Tu implémentes les RÈGLES MÉTIER de l'application Bark & Walk (V1), un club de balades canines à Lyon, EN PAS À PAS. Tu t'adaptes à la stack et à l'architecture déjà présentes dans le repo, sans en imposer une nouvelle.

## Fonctionnement (à chaque étape)

1. Avant de coder : résume en 3 lignes ce que tu vas faire et les fichiers touchés.
2. Une étape = un commit. Ne passe JAMAIS à la suivante sans mon "OK".
3. Chaque règle a au moins un test automatisé : un cas qui passe ET un cas bloquant.
4. Toute valeur "paramétrable" est stockée dans une configuration modifiable (pas en dur dans le code).
5. Les droits et règles sont appliqués côté serveur / données, jamais seulement dans l'interface.
6. Si une règle est ambiguë ou non tranchée, pose-moi la question au lieu de deviner.
7. Fin d'étape : récap court (fait, comment tester, reste à faire).

## Vocabulaire

- Profils : visiteur, inscrit (non-adhérent), adhérent, organisateur communautaire, encadrant salarié, encadrant indépendant, admin. Un compte peut cumuler plusieurs rôles.
- Types de balade : encadrée salarié, encadrée indépendant, communautaire, événement.
- Statuts de réservation : en attente de paiement, confirmée, annulée, terminée, absent.
- Statuts de dossier chien : brouillon, en attente, validé, refusé (avec motif), expiré.

## Étapes

### Étape 0 – Cadrage

Lis le repo, repère où vivent les entités et les règles, puis propose un plan de tests et d'implémentation. Pas de code métier. Attends mon OK.

### Étape 1 – Paramètres métier

Configuration centralisée : annulation 24 h, expiration résa 15 min, liste d'attente 2 h, seuil org 3 balades, retrait org (2 signalements OU note &lt; 3/5 sur 5), avis 14 j, capacité com. 8, commission %, rappels docs J-30/J-7.

### Étape 2 – Droits par profil

Matrice droits + visibilité téléphone / documents + anonymisation compte.

### Étape 3 – Dossier du chien (R2, R16)

### Étape 4 – Compatibilité et capacité (R3, R11)

### Étape 5 – Réservation (R1, R2, R3, R9)

### Étape 6 – Paiement et confirmation (R6, R12)

### Étape 7 – Annulations et remboursements (R4, R5)

### Étape 8 – Liste d'attente (R7)

### Étape 9 – Adhésion (R14)

### Étape 10 – Pointage, balade terminée, droit organisateur (R8, R10)

### Étape 11 – Avis et messagerie (R13)

### Étape 12 – Encadrants indépendants (R15)

### Étape 13 – Automatismes planifiés

### Étape 14 – Recette (matrice R1→R16)

---

*Texte intégral des consignes d’étape : voir le `.docx` ou le message d’origine. Ce fichier sert de point d’entrée versionné dans le repo.*
