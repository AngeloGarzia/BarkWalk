/** Libellés FR pour codes métier affichés à l’utilisateur (les codes API restent en anglais). */

export function walkTypeLabel(type: string | null | undefined): string {
  switch ((type || '').toUpperCase()) {
    case 'COMMUNITY':
      return 'Communautaire';
    case 'STAFF_GUIDED':
      return 'Encadrée (salarié)';
    case 'INDEP_GUIDED':
      return 'Encadrée (indépendant)';
    default:
      return type?.trim() || 'Balade';
  }
}

export function walkStatusLabel(status: string | null | undefined): string {
  switch ((status || '').toUpperCase()) {
    case 'DRAFT':
      return 'Brouillon';
    case 'PUBLISHED':
      return 'Publiée';
    case 'IN_PROGRESS':
      return 'En cours';
    case 'COMPLETED':
      return 'Terminée';
    case 'CANCELLED':
      return 'Annulée';
    default:
      return status?.trim() || '—';
  }
}

export function bookingStatusLabel(status: string | null | undefined): string {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT':
      return 'Paiement en attente';
    case 'CONFIRMED':
      return 'Confirmée';
    case 'CANCELLED_FREE':
      return 'Annulée (gratuite)';
    case 'CANCELLED_LATE':
      return 'Annulée (tardive)';
    case 'COMPLETED':
      return 'Terminée';
    case 'NO_SHOW':
      return 'Absent';
    case 'WAITLISTED':
      return 'Liste d’attente';
    default:
      return status?.trim() || '—';
  }
}

export function waitlistStatusLabel(status: string | null | undefined): string {
  switch ((status || '').toUpperCase()) {
    case 'WAITING':
      return 'En attente';
    case 'OFFERED':
      return 'Place proposée';
    case 'ACCEPTED':
      return 'Acceptée';
    case 'EXPIRED':
      return 'Offre expirée';
    case 'CANCELLED':
      return 'Annulée';
    default:
      return status?.trim() || '—';
  }
}

export function dossierStatusLabel(status: string | null | undefined): string {
  switch ((status || '').toUpperCase()) {
    case 'DRAFT':
      return 'Brouillon';
    case 'PENDING':
      return 'En attente admin';
    case 'VALIDATED':
      return 'Validé';
    case 'REFUSED':
      return 'Refusé';
    case 'EXPIRED':
      return 'Expiré';
    default:
      return status?.trim() || '—';
  }
}

export function reviewStatusLabel(status: string | null | undefined): string {
  switch ((status || '').toUpperCase()) {
    case 'VALIDATED':
    case '':
      return 'Profil validé';
    case 'PENDING':
      return 'En attente admin';
    case 'REFUSED':
      return 'Profil refusé';
    case 'DRAFT':
      return 'Brouillon';
    default:
      return status?.trim() || 'Profil validé';
  }
}

export function roleLabel(role: string | null | undefined): string {
  switch ((role || '').toUpperCase()) {
    case 'USER':
      return 'Inscrit';
    case 'MEMBER':
      return 'Adhérent';
    case 'COMMUNITY_ORG':
      return 'Organisateur';
    case 'WALKER_STAFF':
      return 'Encadrant salarié';
    case 'WALKER_INDEP':
      return 'Encadrant indépendant';
    case 'ADMIN':
      return 'Admin';
    default:
      return role?.trim() || '—';
  }
}

export function sizeLabel(size: string | null | undefined): string {
  switch ((size || '').toUpperCase()) {
    case 'SMALL':
      return 'Petit';
    case 'MEDIUM':
      return 'Moyen';
    case 'LARGE':
      return 'Grand';
    default:
      return size?.trim() || '—';
  }
}

export function energyLabel(level: string | null | undefined): string {
  switch ((level || '').toUpperCase()) {
    case 'LOW':
      return 'Basse';
    case 'MEDIUM':
      return 'Moyenne';
    case 'HIGH':
      return 'Haute';
    default:
      return level?.trim() || '—';
  }
}

export function conversationTypeLabel(type: string | null | undefined): string {
  switch ((type || '').toUpperCase()) {
    case 'WALK':
      return 'Balade';
    case 'DIRECT':
      return 'Privé';
    case 'SUPPORT':
      return 'Support';
    case 'MODERATION':
      return 'Modération';
    default:
      return type?.trim() || 'Conversation';
  }
}
