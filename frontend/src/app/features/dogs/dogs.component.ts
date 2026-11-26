// Liste, création et édition de chiens — champs sensibles → revalidation admin.
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Dog, DogRequest } from '../../models/dog';
import { BreedService } from '../../services/breed.service';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../../shared/photo-uploader/photo-uploader.component';

@Component({
  selector: 'app-dogs',
  standalone: true,
  imports: [ReactiveFormsModule, PhotoUploaderComponent],
  template: `
    <div class="dogs-page">
      <header class="page-header">
        <h1>Mes chiens</h1>
        <p class="subtitle">
          Éditez la fiche librement. Race, taille, énergie, sociabilité, danger et identité
          nécessitent une nouvelle validation admin.
        </p>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
      @if (info()) {
        <p class="banner info" role="status">{{ info() }}</p>
      }

      <section class="panel">
        <h2>{{ editingId() ? 'Modifier ' + (editingName() || 'le chien') : 'Ajouter un chien' }}</h2>
        <form [formGroup]="form" (ngSubmit)="save()" class="form">
          <div class="form-row">
            <label>
              Nom
              <input formControlName="name" type="text" autocomplete="off" />
            </label>
            <label>
              Race
              <input
                formControlName="breed"
                type="text"
                list="breed-options"
                placeholder="Rechercher une race…"
                autocomplete="off"
                (input)="onBreedInput($event)"
              />
              <datalist id="breed-options">
                @for (breed of filteredBreeds(); track breed) {
                  <option [value]="breed"></option>
                }
              </datalist>
              @if (breedsLoading()) {
                <span class="field-hint">Chargement des races…</span>
              }
            </label>
          </div>
          <div class="form-row">
            <label>
              Sexe
              <select formControlName="sex">
                <option value="MALE">Mâle</option>
                <option value="FEMALE">Femelle</option>
              </select>
            </label>
            <label>
              Taille
              <select formControlName="sizeCategory">
                <option value="SMALL">Petit</option>
                <option value="MEDIUM">Moyen</option>
                <option value="LARGE">Grand</option>
              </select>
            </label>
          </div>
          <div class="form-row">
            <label>
              Énergie
              <select formControlName="energyLevel">
                <option value="LOW">Basse</option>
                <option value="MEDIUM">Moyenne</option>
                <option value="HIGH">Haute</option>
              </select>
            </label>
            <label>
              Sociabilité chiens
              <select formControlName="sociabilityDogs">
                <option value="GOOD">Bonne</option>
                <option value="AVERAGE">Moyenne</option>
                <option value="FRAGILE">Fragile</option>
                <option value="UNKNOWN">Inconnue</option>
              </select>
            </label>
          </div>
          <div class="form-row">
            <label>
              Sociabilité humains
              <select formControlName="sociabilityHumans">
                <option value="GOOD">Bonne</option>
                <option value="AVERAGE">Moyenne</option>
                <option value="FRAGILE">Fragile</option>
                <option value="UNKNOWN">Inconnue</option>
              </select>
            </label>
            <label>
              Catégorie danger
              <select formControlName="dangerCategory">
                <option value="">Aucune</option>
                <option value="1">Catégorie 1</option>
                <option value="2">Catégorie 2</option>
              </select>
            </label>
          </div>
          <div class="form-row">
            <label>
              Poids (kg)
              <input formControlName="weightKg" type="number" min="0" step="0.1" />
            </label>
            <label>
              N° identification
              <input formControlName="identificationNumber" type="text" autocomplete="off" />
            </label>
          </div>
          <label>
            Notes d’attention
            <textarea formControlName="attentionNotes" rows="2"></textarea>
          </label>
          <label class="check">
            <input formControlName="sterilized" type="checkbox" />
            Stérilisé
          </label>
          <p class="field-hint sensitive-hint">
            Sensibles (validation admin) : race, sexe, stérilisation, taille, énergie, sociabilité,
            danger, n° ID. Libres : nom, poids, notes, photos.
          </p>
          <div class="form-actions">
            <button type="submit" [disabled]="form.invalid || saving()">
              {{ editingId() ? 'Enregistrer les modifications' : 'Enregistrer' }}
            </button>
            @if (editingId()) {
              <button type="button" class="btn-ghost" (click)="cancelEdit()">Annuler</button>
            }
          </div>
        </form>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>Chiens</h2>
          <button type="button" class="btn-ghost" (click)="reload()">Actualiser</button>
        </div>
        @if (loading()) {
          <p class="muted">Chargement…</p>
        } @else if (dogs().length === 0) {
          <p class="muted">Aucun chien.</p>
        } @else {
          <ul class="dog-list">
            @for (dog of dogs(); track dog.id) {
              <li class="dog-card">
                <div class="dog-top">
                  @if (dog.photos?.length) {
                    <img class="thumb" [src]="dog.photos![0].url" [alt]="dog.name" />
                  }
                  <div class="dog-meta">
                    <strong>{{ dog.name }}</strong>
                    <span>{{ dog.breed }} · {{ sizeLabel(dog.sizeCategory) }}</span>
                    <span class="status" [attr.data-status]="dog.dossierStatus">{{
                      statusLabel(dog.dossierStatus)
                    }}</span>
                    @if (dog.dossierStatus === 'PENDING') {
                      <span class="review-hint">En attente d’approbation admin</span>
                    }
                    @if (dog.dossierStatus === 'REFUSED' && dog.refusalReason) {
                      <span class="review-hint refuse">{{ dog.refusalReason }}</span>
                    }
                  </div>
                  <div class="actions">
                    <button type="button" class="btn-ghost" (click)="startEdit(dog)">Modifier</button>
                    @if (dog.dossierStatus === 'DRAFT') {
                      <button type="button" class="btn-ghost" (click)="submit(dog.id)">Soumettre</button>
                    }
                    <button type="button" class="btn-danger" (click)="remove(dog.id)">Supprimer</button>
                  </div>
                </div>
                <app-photo-uploader
                  [title]="'Photos de ' + dog.name"
                  [uploadUrl]="'/api/dogs/' + dog.id + '/photos'"
                  [initialPhotos]="dog.photos ?? []"
                  (photosChange)="onDogPhotos(dog.id, $event)"
                />
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .dogs-page {
        max-width: 28rem;
        margin: 0 auto;
        padding: 1.1rem 1rem 1.5rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
        color: var(--forest-deep);
      }
      .subtitle {
        color: var(--muted);
        margin: 0 0 1.25rem;
        font-size: 0.92rem;
      }
      .panel {
        background: var(--surface);
        border-radius: var(--radius-md);
        padding: 1.15rem;
        margin-bottom: 1rem;
        border: 1px solid var(--line);
        box-shadow: var(--shadow-sm);
      }
      .panel h2 {
        margin: 0 0 0.85rem;
        font-size: 1.05rem;
        color: var(--forest-deep);
      }
      .form-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.75rem;
      }
      label {
        display: grid;
        gap: 0.35rem;
        margin-bottom: 0.75rem;
        font-size: 0.9rem;
        font-weight: 600;
      }
      label.check {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-weight: 500;
      }
      input,
      select,
      textarea {
        padding: 0.55rem 0.7rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        font: inherit;
        background: var(--bg);
        width: 100%;
        box-sizing: border-box;
      }
      .field-hint {
        font-size: 0.72rem;
        font-weight: 500;
        color: var(--muted);
      }
      .sensitive-hint {
        margin: 0 0 0.85rem;
        line-height: 1.35;
      }
      .form-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.55rem;
      }
      button {
        border: 0;
        border-radius: var(--radius-pill);
        padding: 0.7rem 1.1rem;
        background: var(--cta);
        color: var(--on-cta);
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-ghost {
        background: transparent;
        color: var(--forest);
        border: 1px solid var(--forest-soft);
      }
      .btn-danger {
        background: transparent;
        color: var(--danger);
        border: 1px solid #e8b4b0;
      }
      .panel-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.5rem;
      }
      .panel-head h2 {
        margin: 0;
      }
      .dog-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .dog-card {
        padding: 0.85rem 0 0.25rem;
        border-bottom: 1px solid var(--line);
      }
      .dog-top {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        margin-bottom: 0.35rem;
      }
      .thumb {
        width: 3rem;
        height: 3rem;
        border-radius: 12px;
        object-fit: cover;
        flex-shrink: 0;
        border: 1px solid var(--line);
      }
      .dog-meta {
        flex: 1;
        display: grid;
        gap: 0.2rem;
        min-width: 0;
      }
      .dog-list span {
        color: var(--muted);
        font-size: 0.85rem;
      }
      .status {
        font-weight: 700;
        font-size: 0.78rem !important;
      }
      .status[data-status='VALIDATED'] {
        color: var(--forest);
      }
      .status[data-status='PENDING'] {
        color: var(--warn, #b7791f);
      }
      .status[data-status='REFUSED'],
      .status[data-status='EXPIRED'] {
        color: var(--danger);
      }
      .review-hint {
        font-size: 0.78rem !important;
        color: var(--warn, #b7791f) !important;
      }
      .review-hint.refuse {
        color: var(--danger) !important;
      }
      .actions {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        align-items: stretch;
        flex-shrink: 0;
      }
      .actions button {
        padding: 0.4rem 0.7rem;
        font-size: 0.78rem;
      }
      .muted {
        color: var(--muted);
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
        padding: 0.75rem 1rem;
        border-radius: var(--radius-sm);
      }
      .banner.info {
        background: #e8f2e8;
        color: var(--forest-deep);
        padding: 0.75rem 1rem;
        border-radius: var(--radius-sm);
      }
      @media (max-width: 480px) {
        .form-row {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class DogsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly breedsApi = inject(BreedService);

  protected readonly dogs = signal<Dog[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly breeds = signal<string[]>([]);
  protected readonly breedsLoading = signal(true);
  protected readonly breedQuery = signal('');
  protected readonly editingId = signal<number | null>(null);
  protected readonly editingName = signal<string | null>(null);
  private editingSnapshot: Dog | null = null;

  protected readonly filteredBreeds = computed(() => {
    const q = this.breedQuery().trim().toLowerCase();
    const all = this.breeds();
    if (!q) {
      return all.slice(0, 80);
    }
    return all.filter((b) => b.toLowerCase().includes(q)).slice(0, 80);
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    breed: ['', Validators.required],
    sex: ['MALE', Validators.required],
    sterilized: [false],
    sizeCategory: ['MEDIUM', Validators.required],
    energyLevel: ['MEDIUM', Validators.required],
    sociabilityDogs: ['GOOD', Validators.required],
    sociabilityHumans: ['GOOD', Validators.required],
    dangerCategory: [''],
    weightKg: [null as number | null],
    identificationNumber: [''],
    attentionNotes: [''],
  });

  ngOnInit(): void {
    this.reload();
    this.breedsApi.listBreeds().subscribe({
      next: (list) => {
        this.breeds.set(list);
        this.breedsLoading.set(false);
      },
      error: () => this.breedsLoading.set(false),
    });
  }

  protected onBreedInput(event: Event): void {
    this.breedQuery.set((event.target as HTMLInputElement).value);
  }

  protected onDogPhotos(dogId: number, photos: PhotoItem[]): void {
    this.dogs.update((list) => list.map((d) => (d.id === dogId ? { ...d, photos } : d)));
  }

  protected statusLabel(status: string): string {
    switch (status) {
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
        return status;
    }
  }

  protected sizeLabel(size: string): string {
    switch (size) {
      case 'SMALL':
        return 'Petit';
      case 'MEDIUM':
        return 'Moyen';
      case 'LARGE':
        return 'Grand';
      default:
        return size;
    }
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Dog[]>('/api/dogs').subscribe({
      next: (dogs) => {
        this.dogs.set(dogs);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les chiens.');
        this.loading.set(false);
      },
    });
  }

  protected startEdit(dog: Dog): void {
    this.editingId.set(dog.id);
    this.editingName.set(dog.name);
    this.editingSnapshot = dog;
    this.info.set(null);
    this.error.set(null);
    this.breedQuery.set(dog.breed);
    this.form.reset({
      name: dog.name,
      breed: dog.breed,
      sex: dog.sex || 'MALE',
      sterilized: !!dog.sterilized,
      sizeCategory: dog.sizeCategory || 'MEDIUM',
      energyLevel: dog.energyLevel || 'MEDIUM',
      sociabilityDogs: dog.sociabilityDogs || 'GOOD',
      sociabilityHumans: dog.sociabilityHumans || 'GOOD',
      dangerCategory: dog.dangerCategory ?? '',
      weightKg: dog.weightKg ?? null,
      identificationNumber: dog.identificationNumber ?? '',
      attentionNotes: dog.attentionNotes ?? '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.editingName.set(null);
    this.editingSnapshot = null;
    this.resetForm();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.toRequest();
    const id = this.editingId();
    this.saving.set(true);
    this.error.set(null);
    this.info.set(null);

    const req$ = id
      ? this.http.put<Dog>(`/api/dogs/${id}`, body)
      : this.http.post<Dog>('/api/dogs', body);

    req$.subscribe({
      next: (dog) => {
        this.saving.set(false);
        if (id && dog.dossierStatus === 'PENDING') {
          this.info.set(
            `Modifications de ${dog.name} enregistrées. Une validation admin est requise (champs sensibles).`,
          );
        } else if (id) {
          this.info.set(`Fiche de ${dog.name} mise à jour.`);
        }
        this.cancelEdit();
        this.reload();
      },
      error: () => {
        this.saving.set(false);
        this.error.set(id ? 'Modification impossible.' : 'Création impossible.');
      },
    });
  }

  protected submit(id: number): void {
    this.http.post(`/api/dogs/${id}/submit`, {}).subscribe({
      next: () => {
        this.info.set('Dossier soumis à la validation admin.');
        this.reload();
      },
      error: () => this.error.set('Soumission impossible.'),
    });
  }

  protected remove(id: number): void {
    this.http.delete(`/api/dogs/${id}`).subscribe({
      next: () => {
        if (this.editingId() === id) {
          this.cancelEdit();
        }
        this.reload();
      },
      error: () => this.error.set('Suppression impossible.'),
    });
  }

  private toRequest(): DogRequest {
    const v = this.form.getRawValue();
    const snap = this.editingSnapshot;
    return {
      name: v.name,
      breed: v.breed,
      sex: v.sex,
      sterilized: v.sterilized,
      sizeCategory: v.sizeCategory,
      energyLevel: v.energyLevel,
      sociabilityDogs: v.sociabilityDogs,
      sociabilityHumans: v.sociabilityHumans,
      dangerCategory: v.dangerCategory || null,
      weightKg: v.weightKg,
      identificationNumber: v.identificationNumber || null,
      attentionNotes: v.attentionNotes || null,
      // Préserver les champs non édités dans le formulaire
      birthDate: snap?.birthDate ?? null,
      recallLevel: snap?.recallLevel ?? null,
      leashPulling: snap?.leashPulling ?? null,
    };
  }

  private resetForm(): void {
    this.breedQuery.set('');
    this.form.reset({
      name: '',
      breed: '',
      sex: 'MALE',
      sterilized: false,
      sizeCategory: 'MEDIUM',
      energyLevel: 'MEDIUM',
      sociabilityDogs: 'GOOD',
      sociabilityHumans: 'GOOD',
      dangerCategory: '',
      weightKg: null,
      identificationNumber: '',
      attentionNotes: '',
    });
  }
}
