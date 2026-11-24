import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { Router } from '@angular/router';

interface UserProfile {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  bio?: string | null;
  cityId?: number | null;
  completedWalksCount: number;
  communityOrgEnabled: boolean;
  roles: string[];
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [],
  template: `
    <div class="profile-page">
      <header class="page-header">
        <h1>Mon profil</h1>
      </header>

      @if (loading()) {
        <p class="muted">Chargement…</p>
      } @else if (profile()) {
        <div class="profile-card">
          <div class="avatar">{{ initials() }}</div>
          <div class="profile-info">
            <h2>{{ profile()?.firstName }} {{ profile()?.lastName }}</h2>
            <p class="email">{{ profile()?.email }}</p>
            <div class="roles">
              @for (role of profile()?.roles ?? []; track role) {
                <span class="role-badge">{{ role }}</span>
              }
            </div>
            <p class="muted">Balades terminées : {{ profile()?.completedWalksCount }}</p>
          </div>
        </div>
      } @else if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .profile-page {
        max-width: 640px;
        margin: 0 auto;
        padding: 1.5rem 1rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 1.25rem;
      }
      .profile-card {
        display: flex;
        gap: 1.25rem;
        align-items: center;
        background: #fff;
        border: 1px solid var(--line);
        border-radius: 12px;
        padding: 1.25rem;
      }
      .avatar {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: var(--accent);
        color: #fff;
        display: grid;
        place-items: center;
        font-weight: 700;
        font-size: 1.2rem;
      }
      .email {
        color: var(--muted);
        margin: 0.25rem 0 0.5rem;
      }
      .roles {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
        margin-bottom: 0.5rem;
      }
      .role-badge {
        background: #ebf4f1;
        color: var(--accent);
        border-radius: 999px;
        padding: 0.2rem 0.6rem;
        font-size: 0.75rem;
        font-weight: 600;
      }
      .muted {
        color: var(--muted);
      }
      .banner.error {
        background: #fde8e8;
        color: #9b1c1c;
        padding: 0.75rem 1rem;
        border-radius: 8px;
      }
    `,
  ],
})
export class ProfileComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly initials = () => {
    const p = this.profile();
    if (!p) return '?';
    return `${p.firstName?.[0] ?? ''}${p.lastName?.[0] ?? ''}`.toUpperCase();
  };

  ngOnInit(): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.loading.set(true);
    this.http.get<UserProfile>('/api/me/profile').subscribe({
      next: (p) => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger le profil.');
        this.loading.set(false);
      },
    });
  }
}
