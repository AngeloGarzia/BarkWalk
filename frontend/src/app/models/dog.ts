export interface Dog {
  id: number;
  name: string;
  breed: string;
  birthDate?: string | null;
  sex: string;
  sterilized: boolean;
  weightKg?: number | null;
  sizeCategory: string;
  identificationNumber?: string | null;
  energyLevel: string;
  sociabilityDogs: string;
  sociabilityHumans: string;
  recallLevel?: string | null;
  leashPulling?: string | null;
  attentionNotes?: string | null;
  dangerCategory?: string | null;
  dossierStatus: string;
  refusalReason?: string | null;
  createdAt: string;
}

export interface DogRequest {
  name: string;
  breed: string;
  birthDate?: string | null;
  sex: string;
  sterilized: boolean;
  weightKg?: number | null;
  sizeCategory: string;
  identificationNumber?: string | null;
  energyLevel: string;
  sociabilityDogs: string;
  sociabilityHumans: string;
  recallLevel?: string | null;
  leashPulling?: string | null;
  attentionNotes?: string | null;
  dangerCategory?: string | null;
}
