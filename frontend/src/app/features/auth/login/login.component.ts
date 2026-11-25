// Formulaire de connexion.
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
        <div class="demo-hint">
          <p>Comptes démo (mot de passe <code>Admin123!</code>) :</p>
          <ul>
            <li><button type="button" class="demo-fill" (click)="fillDemo('admin@barkwalk.fr')">admin@barkwalk.fr</button></li>
            <li><button type="button" class="demo-fill" (click)="fillDemo('alice@example.com')">alice@example.com</button></li>
            <li><button type="button" class="demo-fill" (click)="fillDemo('walker@barkwalk.fr')">walker@barkwalk.fr</button></li>
          </ul>
        </div>
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
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-lg);
        padding: 1.75rem 1.5rem;
        width: 100%;
        max-width: 420px;
        box-shadow: var(--shadow-md);
      }
      h1 {
        font-family: var(--font-display);
        margin-top: 0;
        margin-bottom: 1.35rem;
        color: var(--forest-deep);
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
        padding: 0.65rem 0.85rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        font-size: 1rem;
        font-family: var(--font-body);
        outline: none;
        background: var(--bg);
        transition: border-color 0.15s;
      }
      input:focus {
        border-color: var(--forest);
      }
      button {
        width: 100%;
        padding: 0.85rem;
        background: var(--cta);
        color: var(--on-cta);
        border: none;
        border-radius: var(--radius-pill);
        font-size: 1rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
        margin-top: 0.5rem;
        box-shadow: 0 6px 16px rgba(232, 135, 42, 0.28);
        transition: background 0.15s;
      }
      button:hover {
        background: var(--cta-hover);
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
        color: var(--forest);
        font-weight: 700;
      }
      .demo-hint {
        margin-top: 1.25rem;
        padding-top: 1rem;
        border-top: 1px solid var(--line);
        font-size: 0.82rem;
        color: var(--muted);
      }
      .demo-hint p {
        margin: 0 0 0.45rem;
      }
      .demo-hint code {
        font-family: ui-monospace, Consolas, monospace;
        color: var(--forest-deep);
        background: var(--forest-soft);
        padding: 0.1rem 0.35rem;
        border-radius: 6px;
      }
      .demo-hint ul {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      .demo-fill {
        border: none;
        background: transparent;
        color: var(--forest);
        font-weight: 600;
        font-size: 0.82rem;
        cursor: pointer;
        padding: 0.2rem 0;
        text-align: left;
      }
      .demo-fill:hover {
        text-decoration: underline;
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

  protected fillDemo(email: string): void {
    this.form.setValue({ email, password: 'Admin123!' });
    this.error.set(null);
  }

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
