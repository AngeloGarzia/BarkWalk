// Liste et création de chiens — race préremplie via Dog API (dogapi.dog).
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Dog } from '../../models/dog';
import { BreedService } from '../../services/breed.service';

@Component({
  selector: 'app-dogs',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="dogs-page">
      <header class="page-header">
        <h1>Mes chiens</h1>
        <p class="subtitle">Dossiers canins — validation admin requise avant réservation.</p>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }

      <section class="panel">
        <h2>Ajouter un chien</h2>
        <form [formGroup]="form" (ngSubmit)="create()" class="form">
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
              } @else if (breeds().length > 0) {
                <span class="field-hint">{{ breeds().length }} races (Dog API)</span>
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
                <option value="LOW">Faible</option>
                <option value="GOOD">Bonne</option>
                <option value="HIGH">Très bonne</option>
              </select>
            </label>
          </div>
          <label class="check">
            <input formControlName="sterilized" type="checkbox" />
            Stérilisé
          </label>
          <button type="submit" [disabled]="form.invalid || loading()">Enregistrer</button>
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
              <li>
                <div>
                  <strong>{{ dog.name }}</strong>
                  <span>{{ dog.breed }} · {{ dog.sizeCategory }} · {{ dog.dossierStatus }}</span>
                </div>
                <div class="actions">
                  @if (dog.dossierStatus === 'DRAFT') {
                    <button type="button" class="btn-ghost" (click)="submit(dog.id)">Soumettre</button>
                  }
                  <button type="button" class="btn-danger" (click)="remove(dog.id)">Supprimer</button>
                </div>
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
      select {
        padding: 0.55rem 0.7rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        font: inherit;
        background: var(--bg);
      }
      .field-hint {
        font-size: 0.72rem;
        font-weight: 500;
        color: var(--muted);
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
      }
      .dog-list li {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.85rem 0;
        border-bottom: 1px solid var(--line);
      }
      .dog-list li > div {
        display: grid;
        gap: 0.2rem;
      }
      .dog-list span {
        color: var(--muted);
        font-size: 0.85rem;
      }
      .actions {
        display: flex;
        gap: 0.5rem;
        align-items: center;
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
  protected readonly loading = signal(false);
  protected readonly breeds = signal<string[]>([]);
  protected readonly breedsLoading = signal(true);
  protected readonly breedQuery = signal('');

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

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.http.post<Dog>('/api/dogs', this.form.getRawValue()).subscribe({
      next: () => {
        this.form.reset({
          name: '',
          breed: '',
          sex: 'MALE',
          sterilized: false,
          sizeCategory: 'MEDIUM',
          energyLevel: 'MEDIUM',
          sociabilityDogs: 'GOOD',
          sociabilityHumans: 'GOOD',
        });
        this.breedQuery.set('');
        this.reload();
      },
      error: () => this.error.set('Création impossible.'),
    });
  }

  protected submit(id: number): void {
    this.http.post(`/api/dogs/${id}/submit`, {}).subscribe({
      next: () => this.reload(),
      error: () => this.error.set('Soumission impossible.'),
    });
  }

  protected remove(id: number): void {
    this.http.delete(`/api/dogs/${id}`).subscribe({
      next: () => this.reload(),
      error: () => this.error.set('Suppression impossible.'),
    });
  }
}
