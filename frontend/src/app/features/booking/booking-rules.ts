// Règles d'affichage du parcours de réservation, alignées sur BookingService côté API.

export interface PricedWalk {
  walkType: string;
  priceCents: number;
  nonMemberPriceCents?: number | null;
}

export interface CompatibleWalk {
  acceptedSizes: string[];
  acceptedEnergy: string[];
}

export interface EligibleDog {
  id: number;
  dossierStatus: string;
  sizeCategory: string;
  energyLevel: string;
}

export interface Eligibility {
  eligible: boolean;
  reason: string | null;
}

const WAITLISTED_TITLE = 'Walk full — added to waitlist';

const ERROR_MESSAGES: Record<string, string> = {
  'Already booked': 'Ce chien est déjà inscrit à cette balade.',
  'Already on waitlist': 'Ce chien est déjà sur la liste d’attente de cette balade.',
  'Membership required': 'Les balades communautaires sont réservées aux adhérents.',
  'Community disclaimer required': 'Merci d’accepter les conditions de la balade communautaire.',
  'Dog incompatible with walk profile': 'Ce chien ne correspond pas au profil de la balade.',
  'Dog dossier not validated': 'Le dossier de ce chien doit être validé avant de réserver.',
  'Walk not bookable': 'Cette balade n’est plus ouverte aux réservations.',
};

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: 'Confirmée',
  PENDING_PAYMENT: 'Paiement en attente',
  CANCELLED_FREE: 'Annulée',
  CANCELLED_LATE: 'Annulée (tardive)',
  COMPLETED: 'Terminée',
  NO_SHOW: 'Absence',
  WAITLISTED: 'Liste d’attente',
};

const priceFormatter = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

// Même calcul que BookingService.create : l'API reste la source de vérité, ceci sert à l'annonce.
export function bookingPrice(walk: PricedWalk, isMember: boolean, trial: boolean): number {
  if (walk.walkType === 'INDEP_GUIDED') {
    return walk.priceCents;
  }
  if (walk.walkType === 'STAFF_GUIDED' && !isMember && !trial) {
    return walk.nonMemberPriceCents ?? walk.priceCents;
  }
  return 0;
}

export function dogEligibility(
  dog: EligibleDog,
  walk: CompatibleWalk,
  alreadyBookedDogIds: Set<number>,
): Eligibility {
  if (dog.dossierStatus !== 'VALIDATED') {
    return { eligible: false, reason: 'Dossier pas encore validé' };
  }
  if (alreadyBookedDogIds.has(dog.id)) {
    return { eligible: false, reason: 'Déjà inscrit à cette balade' };
  }
  if (!includesIgnoreCase(walk.acceptedSizes, dog.sizeCategory)) {
    return { eligible: false, reason: 'Taille non acceptée sur cette balade' };
  }
  if (!includesIgnoreCase(walk.acceptedEnergy, dog.energyLevel)) {
    return { eligible: false, reason: 'Niveau d’énergie non adapté' };
  }
  return { eligible: true, reason: null };
}

export function isWaitlistedResponse(status: number, title?: string): boolean {
  return status === 409 && title === WAITLISTED_TITLE;
}

export function bookingErrorMessage(status: number, title?: string): string {
  if (status === 401) {
    return 'Votre session a expiré, reconnectez-vous.';
  }
  return (title && ERROR_MESSAGES[title]) || 'La réservation a échoué. Réessayez plus tard.';
}

export function bookingStatusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

export function canCancel(status: string): boolean {
  return status === 'CONFIRMED' || status === 'PENDING_PAYMENT';
}

export function formatPrice(cents: number): string {
  return cents <= 0 ? 'Gratuit' : priceFormatter.format(cents / 100);
}

function includesIgnoreCase(values: string[] | null | undefined, value: string | null | undefined): boolean {
  if (!values || !value) {
    return false;
  }
  return values.some((v) => v?.toLowerCase() === value.toLowerCase());
}
