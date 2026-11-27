import { describe, expect, it } from 'vitest';
import { bookingStatusLabel, walkStatusLabel, walkTypeLabel } from './labels';

describe('labels', () => {
  // Traduit le type de balade pour l’UI.
  it('libellé type de balade', () => {
    expect(walkTypeLabel('COMMUNITY')).toBe('Communautaire');
    expect(walkTypeLabel('STAFF_GUIDED')).toBe('Encadrée (salarié)');
    expect(walkTypeLabel('INDEP_GUIDED')).toBe('Encadrée (indépendant)');
  });

  // Traduit le statut de balade.
  it('libellé statut de balade', () => {
    expect(walkStatusLabel('PUBLISHED')).toBe('Publiée');
    expect(walkStatusLabel('IN_PROGRESS')).toBe('En cours');
    expect(walkStatusLabel('CANCELLED')).toBe('Annulée');
  });

  // Traduit le statut de réservation.
  it('libellé statut de réservation', () => {
    expect(bookingStatusLabel('PENDING_PAYMENT')).toBe('Paiement en attente');
    expect(bookingStatusLabel('CONFIRMED')).toBe('Confirmée');
    expect(bookingStatusLabel('NO_SHOW')).toBe('Absent');
  });
});
