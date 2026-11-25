import {
  bookingErrorMessage,
  bookingPrice,
  bookingStatusLabel,
  canCancel,
  dogEligibility,
  formatPrice,
  isWaitlistedResponse,
} from './booking-rules';

const walk = {
  walkType: 'STAFF_GUIDED',
  priceCents: 0,
  nonMemberPriceCents: 1500,
  acceptedSizes: ['MEDIUM', 'LARGE'],
  acceptedEnergy: ['MEDIUM', 'HIGH'],
};

const dog = { id: 1, dossierStatus: 'VALIDATED', sizeCategory: 'LARGE', energyLevel: 'HIGH' };

describe('bookingPrice', () => {
  it('is free for a member on a staff walk', () => {
    expect(bookingPrice(walk, true, false)).toBe(0);
  });

  it('charges the non-member price on a staff walk', () => {
    expect(bookingPrice(walk, false, false)).toBe(1500);
  });

  it('is free for a non-member using the trial walk', () => {
    expect(bookingPrice(walk, false, true)).toBe(0);
  });

  it('falls back to the base price when no non-member price is set', () => {
    expect(bookingPrice({ ...walk, priceCents: 900, nonMemberPriceCents: null }, false, false)).toBe(900);
  });

  it('always charges the walker price on an independent walk, even for members', () => {
    expect(bookingPrice({ ...walk, walkType: 'INDEP_GUIDED', priceCents: 2000 }, true, false)).toBe(2000);
  });

  it('is free on a community walk', () => {
    expect(bookingPrice({ ...walk, walkType: 'COMMUNITY', priceCents: 0 }, true, false)).toBe(0);
  });
});

describe('dogEligibility', () => {
  it('accepts a validated dog that matches the walk profile', () => {
    expect(dogEligibility(dog, walk, new Set())).toEqual({ eligible: true, reason: null });
  });

  it('refuses a dog whose dossier is not validated yet', () => {
    expect(dogEligibility({ ...dog, dossierStatus: 'SUBMITTED' }, walk, new Set())).toEqual({
      eligible: false,
      reason: 'Dossier pas encore validé',
    });
  });

  it('refuses a dog already booked on this walk', () => {
    expect(dogEligibility(dog, walk, new Set([1]))).toEqual({
      eligible: false,
      reason: 'Déjà inscrit à cette balade',
    });
  });

  it('refuses a dog whose size is not accepted', () => {
    expect(dogEligibility({ ...dog, sizeCategory: 'SMALL' }, walk, new Set()).reason).toBe(
      'Taille non acceptée sur cette balade',
    );
  });

  it('refuses a dog whose energy level does not fit', () => {
    expect(dogEligibility({ ...dog, energyLevel: 'LOW' }, walk, new Set()).reason).toBe(
      'Niveau d’énergie non adapté',
    );
  });

  it('compares sizes and energy case-insensitively, like the API', () => {
    expect(dogEligibility({ ...dog, sizeCategory: 'large', energyLevel: 'high' }, walk, new Set()).eligible).toBe(
      true,
    );
  });
});

describe('isWaitlistedResponse', () => {
  it('recognises the walk-full conflict that put the dog on the waitlist', () => {
    expect(isWaitlistedResponse(409, 'Walk full — added to waitlist')).toBe(true);
  });

  it('does not treat other conflicts as a waitlist placement', () => {
    expect(isWaitlistedResponse(409, 'Already on waitlist')).toBe(false);
    expect(isWaitlistedResponse(409, 'Already booked')).toBe(false);
  });
});

describe('bookingErrorMessage', () => {
  it.each([
    [409, 'Already booked', 'Ce chien est déjà inscrit à cette balade.'],
    [409, 'Already on waitlist', 'Ce chien est déjà sur la liste d’attente de cette balade.'],
    [403, 'Membership required', 'Les balades communautaires sont réservées aux adhérents.'],
    [400, 'Community disclaimer required', 'Merci d’accepter les conditions de la balade communautaire.'],
    [400, 'Dog incompatible with walk profile', 'Ce chien ne correspond pas au profil de la balade.'],
    [400, 'Dog dossier not validated', 'Le dossier de ce chien doit être validé avant de réserver.'],
    [400, 'Walk not bookable', 'Cette balade n’est plus ouverte aux réservations.'],
  ])('translates %s "%s"', (status, title, expected) => {
    expect(bookingErrorMessage(status, title)).toBe(expected);
  });

  it('asks to log in again when the session expired', () => {
    expect(bookingErrorMessage(401, undefined)).toBe('Votre session a expiré, reconnectez-vous.');
  });

  it('falls back to a generic message for anything unexpected', () => {
    expect(bookingErrorMessage(500, 'Internal Server Error')).toBe('La réservation a échoué. Réessayez plus tard.');
  });
});

describe('bookingStatusLabel', () => {
  it.each([
    ['CONFIRMED', 'Confirmée'],
    ['PENDING_PAYMENT', 'Paiement en attente'],
    ['CANCELLED_FREE', 'Annulée'],
    ['CANCELLED_LATE', 'Annulée (tardive)'],
    ['COMPLETED', 'Terminée'],
    ['NO_SHOW', 'Absence'],
  ])('labels %s as %s', (status, label) => {
    expect(bookingStatusLabel(status)).toBe(label);
  });
});

describe('canCancel', () => {
  it('allows cancelling confirmed and pending-payment bookings only', () => {
    expect(canCancel('CONFIRMED')).toBe(true);
    expect(canCancel('PENDING_PAYMENT')).toBe(true);
    expect(canCancel('CANCELLED_FREE')).toBe(false);
    expect(canCancel('COMPLETED')).toBe(false);
  });
});

describe('formatPrice', () => {
  it('shows free bookings as "Gratuit"', () => {
    expect(formatPrice(0)).toBe('Gratuit');
  });

  it('formats cents as euros, French style', () => {
    expect(formatPrice(1250)).toMatch(/^12,50\s€$/);
  });
});
