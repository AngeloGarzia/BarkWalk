// Consultation du profil connecté + 1 à 3 photos.
import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { Router, RouterLink } from '@angular/router';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../../shared/photo-uploader/photo-uploader.component';

interface UserProfile {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  bio?: string | null;
  cityId?: number | null;
  completedWalksCount: number;
  communityOrgStatus: string;
  roles: string[];
  photos?: PhotoItem[];
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [PhotoUploaderComponent, RouterLink],
  template: `
    <div class="profile-page">
      <header class="page-header">
        <h1>Mon profil</h1>
      </header>

      @if (loading()) {
        <p class="muted">Chargement…</p>
      } @else if (profile(); as p) {
        <div class="profile-card">
          @if (p.photos?.length) {
            <img class="avatar-img" [src]="p.photos![0].url" [alt]="p.firstName" />
          } @else {
            <div class="avatar">{{ initials() }}</div>
          }
          <div class="profile-info">
            <h2>{{ p.firstName }} {{ p.lastName }}</h2>
            <p class="email">{{ p.email }}</p>
            <div class="roles">
              @for (role of p.roles ?? []; track role) {
                <span class="role-badge">{{ role }}</span>
              }
            </div>
            <p class="muted">Balades terminées : {{ p.completedWalksCount }}</p>
          </div>
        </div>

        @if (isAdmin()) {
          <a routerLink="/admin" class="admin-link">Administration</a>
        }

        <section class="panel">
          <app-photo-uploader
            title="Mes photos"
            uploadUrl="/api/me/photos"
            [initialPhotos]="p.photos ?? []"
            (photosChange)="onPhotosChange($event)"
          />
        </section>

        <button type="button" class="logout" (click)="logout()">Déconnexion</button>
      } @else if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .profile-page {
        max-width: 28rem;
        margin: 0 auto;
        padding: 1.1rem 1rem 1.5rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 1.25rem;
        color: var(--forest-deep);
      }
      .profile-card {
        display: flex;
        gap: 1.25rem;
        align-items: center;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 1.25rem;
        box-shadow: var(--shadow-sm);
        margin-bottom: 0.85rem;
      }
      .panel {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 1rem 1.1rem;
        box-shadow: var(--shadow-sm);
      }
      .avatar,
      .avatar-img {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        flex-shrink: 0;
      }
      .avatar {
        background: var(--forest);
        color: var(--on-forest);
        display: grid;
        place-items: center;
        font-weight: 800;
        font-family: var(--font-display);
        font-size: 1.2rem;
      }
      .avatar-img {
        object-fit: cover;
        border: 2px solid var(--forest-soft);
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
        background: var(--forest-soft);
        color: var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.2rem 0.6rem;
        font-size: 0.75rem;
        font-weight: 600;
      }
      .muted {
        color: var(--muted);
      }
      .admin-link {
        display: block;
        text-align: center;
        text-decoration: none;
        margin-bottom: 0.85rem;
        padding: 0.75rem 1rem;
        border-radius: var(--radius-md);
        border: 1px solid var(--line);
        background: var(--surface);
        color: var(--forest-deep);
        font-weight: 700;
        font-family: var(--font-display);
        box-shadow: var(--shadow-sm);
      }
      .admin-link:hover {
        border-color: var(--forest);
        color: var(--forest);
      }
      .logout {
        margin-top: 1.25rem;
        width: 100%;
        min-height: 2.75rem;
        border-radius: var(--radius-pill);
        border: 1.5px solid #e8b4b0;
        background: transparent;
        color: var(--danger);
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
        padding: 0.75rem 1rem;
        border-radius: var(--radius-sm);
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
  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected readonly initials = () => {
    const p = this.profile();
    if (!p) return '?';
    return `${p.firstName?.[0] ?? ''}${p.lastName?.[0] ?? ''}`.toUpperCase();
  };

  protected onPhotosChange(photos: PhotoItem[]): void {
    const current = this.profile();
    if (!current) {
      return;
    }
    this.profile.set({ ...current, photos });
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

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
