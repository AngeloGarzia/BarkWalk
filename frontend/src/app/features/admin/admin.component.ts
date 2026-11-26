// Tableau de bord administration.
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface DashboardStats {
  users: number;
  walks: number;
  bookings: number;
  memberships: number;
  revenueCents: number;
  commissionCents: number;
}

interface PendingDog {
  id: number;
  name: string;
  breed: string;
  sizeCategory?: string;
  energyLevel?: string;
  sociabilityDogs?: string;
  dangerCategory?: string | null;
  identificationNumber?: string | null;
  dossierStatus: string;
  createdAt: string;
}

interface PendingProfile {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  cityId?: number | null;
  reviewStatus: string;
}

interface ArchivedWalk {
  id: number;
  title: string;
  status: string;
  startsAt: string;
  archivedAt?: string | null;
  bookedCount: number;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="admin-page">
      <header class="page-header">
        <div class="header-row">
          <div>
            <h1>Administration</h1>
            <p class="subtitle">Tableau de bord, validation chiens et profils.</p>
          </div>
          <a routerLink="/admin/settings" class="btn-ghost">Paramètres</a>
        </div>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }

      @if (stats()) {
        <div class="stats-grid">
          <div class="stat-card">
            <span class="stat-value">{{ stats()?.users }}</span>
            <span class="stat-label">Utilisateurs</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats()?.bookings }}</span>
            <span class="stat-label">Réservations</span>
          </div>
          <div class="stat-card accent">
            <span class="stat-value">{{ pendingDogs().length }}</span>
            <span class="stat-label">Chiens en attente</span>
          </div>
          <div class="stat-card accent">
            <span class="stat-value">{{ pendingProfiles().length }}</span>
            <span class="stat-label">Profils en attente</span>
          </div>
        </div>
      }

      <section class="panel" aria-labelledby="pending-profiles-title">
        <div class="panel-head">
          <h2 id="pending-profiles-title">Profils en attente de validation</h2>
          <button class="btn-ghost" (click)="loadPendingProfiles()" [disabled]="loadingProfiles()">
            Actualiser
          </button>
        </div>

        @if (loadingProfiles()) {
          <p class="muted">Chargement…</p>
        } @else if (pendingProfiles().length === 0) {
          <p class="muted">Aucun profil en attente.</p>
        } @else {
          <ul class="pending-list">
            @for (profile of pendingProfiles(); track profile.userId) {
              <li class="pending-item">
                <div class="dog-info">
                  <strong>{{ profile.firstName }} {{ profile.lastName }}</strong>
                  <span>{{ profile.email }}</span>
                  <span class="dog-detail"
                    >Tél. {{ profile.phone || '—' }}
                    @if (profile.cityId) {
                      · Ville #{{ profile.cityId }}
                    }
                  </span>
                </div>
                <div class="actions">
                  <button class="btn-approve" (click)="approveProfile(profile.userId)">Approuver</button>
                  <button class="btn-reject" (click)="rejectProfile(profile.userId)">Rejeter</button>
                </div>
              </li>
            }
          </ul>
        }
      </section>

      <section class="panel" aria-labelledby="pending-title">
        <div class="panel-head">
          <h2 id="pending-title">Chiens en attente de validation</h2>
          <button class="btn-ghost" (click)="loadPending()" [disabled]="loadingPending()">
            Actualiser
          </button>
        </div>

        @if (loadingPending()) {
          <p class="muted">Chargement…</p>
        } @else if (pendingDogs().length === 0) {
          <p class="muted">Aucun chien en attente de validation.</p>
        } @else {
          <ul class="pending-list">
            @for (dog of pendingDogs(); track dog.id) {
              <li class="pending-item">
                <div class="dog-info">
                  <strong>{{ dog.name }}</strong>
                  <span>{{ dog.breed }} · {{ dog.sizeCategory }} · {{ dog.energyLevel }}</span>
                  <span class="dog-detail"
                    >Soc. chiens {{ dog.sociabilityDogs }}
                    @if (dog.dangerCategory) {
                      · Danger {{ dog.dangerCategory }}
                    }
                    @if (dog.identificationNumber) {
                      · ID {{ dog.identificationNumber }}
                    }
                  </span>
                </div>
                <div class="actions">
                  <button class="btn-approve" (click)="approve(dog.id)">Approuver</button>
                  <button class="btn-reject" (click)="reject(dog.id)">Rejeter</button>
                </div>
              </li>
            }
          </ul>
        }
      </section>

      <section class="panel archived-panel" aria-labelledby="archived-title">
        <div class="panel-head">
          <h2 id="archived-title">Balades archivées</h2>
          <button class="btn-ghost" (click)="loadArchived()" [disabled]="loadingArchived()">
            Actualiser
          </button>
        </div>
        <p class="muted hint">
          Les balades sont archivées (pas supprimées). Seul un admin peut les supprimer
          définitivement, et seulement sans réservation liée.
        </p>
        @if (loadingArchived()) {
          <p class="muted">Chargement…</p>
        } @else if (archivedWalks().length === 0) {
          <p class="muted">Aucune balade archivée.</p>
        } @else {
          <ul class="pending-list">
            @for (walk of archivedWalks(); track walk.id) {
              <li class="pending-item">
                <div class="dog-info">
                  <strong>{{ walk.title }}</strong>
                  <span
                    >{{ walk.status }} · {{ walk.startsAt | date: 'short' }}
                    @if (walk.archivedAt) {
                      · archivée {{ walk.archivedAt | date: 'short' }}
                    }
                  </span>
                  <span class="dog-detail">{{ walk.bookedCount }} réservation(s)</span>
                </div>
                <div class="actions">
                  <button
                    class="btn-reject"
                    [disabled]="actingWalkId() === walk.id || walk.bookedCount > 0"
                    (click)="deleteWalk(walk)"
                    [title]="
                      walk.bookedCount > 0
                        ? 'Suppression impossible : réservations liées'
                        : 'Suppression définitive'
                    "
                  >
                    Supprimer
                  </button>
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
      .admin-page {
        max-width: 900px;
        margin: 0 auto;
        padding: 1.5rem 1rem;
      }
      .page-header {
        margin-bottom: 1.5rem;
      }
      .header-row {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        flex-wrap: wrap;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
      }
      .subtitle {
        color: var(--muted);
        margin: 0;
      }
      .stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
        gap: 1rem;
        margin-bottom: 1.5rem;
      }
      .stat-card {
        background: white;
        border-radius: 12px;
        padding: 1.25rem;
        text-align: center;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      }
      .stat-card.accent {
        background: var(--accent);
        color: white;
      }
      .stat-value {
        display: block;
        font-size: 2rem;
        font-weight: 700;
        font-family: var(--font-display);
      }
      .stat-label {
        font-size: 0.85rem;
        opacity: 0.8;
      }
      .panel {
        background: white;
        border-radius: 12px;
        padding: 1.5rem;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      }
      .archived-panel {
        margin-top: 1.25rem;
      }
      .hint {
        margin: 0 0 0.85rem;
        font-size: 0.85rem;
      }
      .panel h2 {
        font-family: var(--font-display);
        font-size: 1.1rem;
        margin: 0;
      }
      .panel-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 1rem;
      }
      .btn-ghost {
        background: transparent;
        border: 1px solid var(--line);
        border-radius: 8px;
        padding: 0.4rem 0.9rem;
        font-size: 0.88rem;
        cursor: pointer;
        color: var(--muted);
      }
      .btn-ghost:hover {
        border-color: var(--accent);
        color: var(--accent);
      }
      .pending-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .pending-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.75rem 1rem;
        background: var(--bg);
        border-radius: 8px;
        gap: 1rem;
        flex-wrap: wrap;
      }
      .dog-info {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .dog-info span {
        font-size: 0.85rem;
        color: var(--muted);
      }
      .dog-detail {
        font-size: 0.8rem !important;
        line-height: 1.35;
      }
      .actions {
        display: flex;
        gap: 0.5rem;
      }
      .btn-approve {
        background: var(--accent);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 0.35rem 0.8rem;
        font-size: 0.85rem;
        font-weight: 600;
        cursor: pointer;
      }
      .btn-reject {
        background: transparent;
        border: 1px solid #e53e3e;
        color: #e53e3e;
        border-radius: 8px;
        padding: 0.35rem 0.8rem;
        font-size: 0.85rem;
        font-weight: 600;
        cursor: pointer;
      }
      .muted {
        color: var(--muted);
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
export class AdminComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly pendingDogs = signal<PendingDog[]>([]);
  protected readonly pendingProfiles = signal<PendingProfile[]>([]);
  protected readonly archivedWalks = signal<ArchivedWalk[]>([]);
  protected readonly loadingPending = signal(false);
  protected readonly loadingProfiles = signal(false);
  protected readonly loadingArchived = signal(false);
  protected readonly actingWalkId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDashboard();
    this.loadPending();
    this.loadPendingProfiles();
    this.loadArchived();
  }

  private loadDashboard(): void {
    this.http.get<DashboardStats>('/api/admin/dashboard').subscribe({
      next: stats => this.stats.set(stats),
      error: () => this.error.set('Impossible de charger le tableau de bord.'),
    });
  }

  protected loadPending(): void {
    this.loadingPending.set(true);
    this.http.get<PendingDog[]>('/api/admin/dogs/pending').subscribe({
      next: dogs => {
        this.pendingDogs.set(dogs);
        this.loadingPending.set(false);
      },
      error: () => this.loadingPending.set(false),
    });
  }

  protected loadPendingProfiles(): void {
    this.loadingProfiles.set(true);
    this.http.get<PendingProfile[]>('/api/admin/profiles/pending').subscribe({
      next: (profiles) => {
        this.pendingProfiles.set(profiles);
        this.loadingProfiles.set(false);
      },
      error: () => this.loadingProfiles.set(false),
    });
  }

  protected approve(id: number): void {
    this.http.post(`/api/admin/dogs/${id}/validate`, {}).subscribe({
      next: () => {
        this.loadPending();
        this.loadDashboard();
      },
      error: () => this.error.set("Échec de l'approbation."),
    });
  }

  protected reject(id: number): void {
    this.http.post(`/api/admin/dogs/${id}/refuse`, { reason: 'Dossier incomplet' }).subscribe({
      next: () => this.loadPending(),
      error: () => this.error.set('Échec du rejet.'),
    });
  }

  protected approveProfile(userId: number): void {
    this.http.post(`/api/admin/profiles/${userId}/validate`, {}).subscribe({
      next: () => this.loadPendingProfiles(),
      error: () => this.error.set("Échec de l'approbation du profil."),
    });
  }

  protected rejectProfile(userId: number): void {
    this.http
      .post(`/api/admin/profiles/${userId}/refuse`, { reason: 'Informations à corriger' })
      .subscribe({
        next: () => this.loadPendingProfiles(),
        error: () => this.error.set('Échec du rejet du profil.'),
      });
  }

  protected loadArchived(): void {
    this.loadingArchived.set(true);
    this.http.get<ArchivedWalk[]>('/api/admin/walks/archived').subscribe({
      next: (walks) => {
        this.archivedWalks.set(walks);
        this.loadingArchived.set(false);
      },
      error: () => {
        this.loadingArchived.set(false);
        this.error.set('Impossible de charger les balades archivées.');
      },
    });
  }

  protected deleteWalk(walk: ArchivedWalk): void {
    if (walk.bookedCount > 0 || this.actingWalkId() != null) {
      return;
    }
    if (!confirm(`Supprimer définitivement « ${walk.title} » ?`)) {
      return;
    }
    this.actingWalkId.set(walk.id);
    this.http.delete(`/api/admin/walks/${walk.id}`).subscribe({
      next: () => {
        this.actingWalkId.set(null);
        this.loadArchived();
        this.loadDashboard();
      },
      error: (err) => {
        this.actingWalkId.set(null);
        this.error.set(err?.error?.message || 'Suppression impossible.');
      },
    });
  }
}
