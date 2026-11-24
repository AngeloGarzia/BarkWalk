import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1>Créer un compte</h1>
        @if (error()) {
          <p class="banner error" role="alert">{{ error() }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="row">
            <label>
              Prénom
              <input formControlName="firstName" type="text" autocomplete="given-name" />
            </label>
            <label>
              Nom
              <input formControlName="lastName" type="text" autocomplete="family-name" />
            </label>
          </div>
          <label>
            E-mail
            <input formControlName="email" type="email" autocomplete="email" />
          </label>
          <label>
            Mot de passe
            <input
              formControlName="password"
              type="password"
              autocomplete="new-password"
              placeholder="8 caractères minimum"
            />
          </label>
          <label class="check">
            <input formControlName="acceptCgu" type="checkbox" />
            J’accepte les CGU et la charte
          </label>
          <button type="submit" [disabled]="loading() || form.invalid">
            {{ loading() ? 'Inscription…' : "S'inscrire" }}
          </button>
        </form>
        <p class="auth-link">
          Déjà inscrit&nbsp;? <a routerLink="/login">Se connecter</a>
        </p>
      </div>
    </div>
  `,
  styles: [
    `
      .auth-page {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 70vh;
        padding: 1rem;
      }
      .auth-card {
        background: white;
        border-radius: 12px;
        padding: 2rem;
        width: 100%;
        max-width: 460px;
        box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
      }
      h1 {
        font-family: var(--font-display);
        margin-top: 0;
        margin-bottom: 1.5rem;
      }
      .row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.75rem;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-bottom: 1rem;
        font-size: 0.9rem;
        font-weight: 600;
        color: var(--ink);
      }
      label.check {
        flex-direction: row;
        align-items: center;
        gap: 0.5rem;
        font-weight: 500;
      }
      input {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--line);
        border-radius: 8px;
        font-size: 1rem;
        font-family: var(--font-body);
        outline: none;
        transition: border-color 0.15s;
      }
      input:focus {
        border-color: var(--accent);
      }
      button {
        width: 100%;
        padding: 0.75rem;
        background: var(--accent);
        color: white;
        border: none;
        border-radius: 8px;
        font-size: 1rem;
        font-weight: 600;
        cursor: pointer;
        margin-top: 0.5rem;
        transition: opacity 0.15s;
      }
      button:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .auth-link {
        text-align: center;
        margin-top: 1.25rem;
        color: var(--muted);
        font-size: 0.9rem;
      }
      .auth-link a {
        color: var(--accent);
        font-weight: 600;
      }
      .banner.error {
        background: #fde8e8;
        color: #9b1c1c;
        padding: 0.75rem 1rem;
        border-radius: 8px;
        margin-bottom: 1rem;
        font-size: 0.9rem;
      }
    `,
  ],
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    acceptCgu: [false, Validators.requiredTrue],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    this.auth.register({ ...raw, cityId: null }).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => {
        this.error.set("Échec de l'inscription. Cet e-mail est peut-être déjà utilisé.");
        this.loading.set(false);
      },
    });
  }
}
