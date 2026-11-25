// Types TypeScript alignés sur l'API balades et réservations.
export interface Walk {
  id: number;
  cityId: number;
  cityName: string | null;
  meetingPointId: number;
  meetingPointName: string | null;
  meetingPointAddress: string | null;
  lat: number | null;
  lng: number | null;
  walkType: string;
  title: string;
  description: string | null;
  startsAt: string;
  durationMinutes: number;
  capacity: number;
  bookedCount: number;
  priceCents: number;
  nonMemberPriceCents: number | null;
  status: string;
  acceptedSizes: string[];
  acceptedEnergy: string[];
  routeHint: string | null;
  communityDisclaimerRequired: boolean;
}

export interface Booking {
  id: number;
  walkId: number;
  dogId: number;
  status: string;
  priceCents: number;
  paymentRequired: boolean;
  confirmedAt: string | null;
}

export interface BookingRequest {
  walkId: number;
  dogId: number;
  communityAck: boolean;
  trial: boolean;
}
