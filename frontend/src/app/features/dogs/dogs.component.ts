import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Dog } from '../../models/dog';

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
            <label>Nom <input formControlName="name" type="text" /></label>
            <label>Race <input formControlName="breed" type="text" /></label>
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
      .dogs-page { max-width: 760px; margin: 0 auto; padding: 1.5rem 1rem; }
      h1 { font-family: var(--font-display); margin: 0 0 0.25rem; }
      .subtitle { color: var(--muted); margin: 0 0 1.5rem; }
      .panel { background: #fff; border-radius: 12px; padding: 1.25rem; margin-bottom: 1rem; border: 1px solid var(--line); }
      .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
      label { display: grid; gap: 0.35rem; margin-bottom: 0.75rem; font-size: 0.9rem; }
      label.check { display: flex; align-items: center; gap: 0.5rem; }
      input, select { padding: 0.55rem 0.7rem; border: 1px solid var(--line); border-radius: 8px; font: inherit; }
      button { border: 0; border-radius: 8px; padding: 0.65rem 1rem; background: var(--accent); color: #fff; font-weight: 600; cursor: pointer; }
      .btn-ghost { background: transparent; color: var(--accent); border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent); }
      .btn-danger { background: transparent; color: #9b2c2c; border: 1px solid #f0c2c2; }
      .panel-head { display: flex; justify-content: space-between; align-items: center; }
      .dog-list { list-style: none; margin: 0; padding: 0; }
      .dog-list li { display: flex; justify-content: space-between; gap: 1rem; padding: 0.85rem 0; border-bottom: 1px solid var(--line); }
      .dog-list li > div { display: grid; gap: 0.2rem; }
      .actions { display: flex; gap: 0.5rem; align-items: center; }
      .muted { color: var(--muted); }
      .banner.error { background: #fde8e8; color: #9b2c2c; padding: 0.75rem 1rem; border-radius: 8px; }
    `,
  ],
})
export class DogsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  protected readonly dogs = signal<Dog[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

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
