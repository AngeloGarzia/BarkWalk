import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1>Connexion</h1>
        @if (error()) {
          <p class="banner error" role="alert">{{ error() }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            E-mail
            <input formControlName="email" type="email" autocomplete="email" />
          </label>
          <label>
            Mot de passe
            <input formControlName="password" type="password" autocomplete="current-password" />
          </label>
          <button type="submit" [disabled]="loading() || form.invalid">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
        </form>
        <p class="auth-link">
          Pas encore inscrit&nbsp;? <a routerLink="/register">Créer un compte</a>
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
        max-width: 420px;
        box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
      }
      h1 {
        font-family: var(--font-display);
        margin-top: 0;
        margin-bottom: 1.5rem;
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
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => {
        this.error.set('Identifiants incorrects. Vérifiez votre e-mail et mot de passe.');
        this.loading.set(false);
      },
    });
  }
}
