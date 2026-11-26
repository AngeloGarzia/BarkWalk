# Recette métier Bark & Walk — matrice R1 → R16

Date : 2026-11-26 — étapes 1–13 implémentées côté serveur.

| Règle | Implémentation | Test | Statut |
|-------|----------------|------|--------|
| R1 Essai gratuit 1× / STAFF | `BookingService.create` + `existsByUserIdAndTrialTrue` | `BookingServiceTest.create_rejectsSecondTrial`, `create_rejectsTrialOnIndepWalk` | OK |
| R2 Dossier chien validé | `DogDossierService.assertBookable` | `DogDossierServiceTest`, `BookingServiceTest` | OK |
| R3 Compatibilité + motif | `DogWalkCompatibilityService` | `DogWalkCompatibilityServiceTest` (size/energy/sociability) | OK |
| R4 Annulation 24 h | `BookingService.cancel` + `cancellationFreeHours` | `BookingServiceTest.cancel_marksCancelledFree*`, `cancel_marksCancelledLate*` | OK |
| R5 Annulation staff + refund | `BookingService.cancelByStaff` + `NotificationService` | `BookingServiceTest.cancelByStaff_refundsAndNotifies` | OK (email stub) |
| R6 Place non payée | `BookingService.expireUnpaidBookings` + jobs | `BookingServiceTest.expireUnpaidBookings_releasesPlace` | OK |
| R7 Liste d'attente | `WaitlistService` | `WaitlistServiceTest` | OK |
| R8 Pointage / org éligible | `AttendanceService` + `CommunityOrgService.onWalkCompleted` | `CommunityOrgServiceTest.onWalkCompleted_*` | OK |
| R9 Communautaire ack + membre | `BookingService.create` | `BookingServiceTest` community tests | OK |
| R10 Retrait org | `CommunityOrgService.evaluateRevocation` | `CommunityOrgServiceTest.evaluateRevocation_*` | OK |
| R11 Capacité com. | `WalkService.assertCapacityAllowed` + settings | `WalkServiceTest.create_community_rejectsCapacityAboveSetting` | OK |
| R12 Commission indep | `CommissionCalculator` + webhook paiement | `CommissionCalculatorTest`, `PaymentServiceTest` | OK |
| R13 Avis + messagerie | `ReviewService`, `MessagingService` | `ReviewServiceTest`, `MessagingServiceTest` | OK |
| R14 Adhésion | `MembershipService.activateOnPayment` | `MembershipServiceTest` | OK |
| R15 Indépendants publish | `WalkerPublishEligibility` | `WalkerPublishEligibilityTest` | OK |
| R16 Docs expirés | `DogDossierService` + scheduler | `DogDossierServiceTest.refreshExpiry_*` | OK |

## Non couvert / partiel / ambigu

| Sujet | Détail |
|-------|--------|
| Catégorie chien 1/2 | Déclarable ; règle acceptation/refus/balades dédiées **non tranchée** |
| Email réel | `NotificationService` = stub logs (pas de provider SMTP/Sendgrid) |
| Stripe live | Webhook de dev + signature optionnelle ; pas de Checkout Session complète |
| `INDEP_GUIDED` en DB | App gère le type ; CHECK V3 historique `STAFF_GUIDED\|COMMUNITY` — à aligner si prod indep |
| Rappels J-30/J-7 docs | Settings lus + stub email ; sélection des docs à rappeler à enrichir |
| Rappel veille de balade | Stub `notifyWalkTomorrow` ; pas encore de requête walks du lendemain |
| Concurrence dernière place | Lock pessimiste présent ; pas de test multi-thread dédié |
| Photos profil/chien | WIP local non inclus dans les commits métier |

## Commande de vérification

```powershell
cd backend
mvn "-Dtest=DogDossierServiceTest,DogWalkCompatibilityServiceTest,BookingServiceTest,WaitlistServiceTest,PaymentServiceTest,CommissionCalculatorTest,MembershipServiceTest,CommunityOrgServiceTest,ReviewServiceTest,MessagingServiceTest,WalkerPublishEligibilityTest,WalkServiceTest,BusinessJobsSchedulerTest,BusinessSettingsServiceTest,PrivacyServiceTest" test
```
