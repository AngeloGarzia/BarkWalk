import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

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
  dossierStatus: string;
  createdAt: string;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [],
  template: `
    <div class="admin-page">
      <header class="page-header">
        <h1>Administration</h1>
        <p class="subtitle">Tableau de bord et validation des chiens.</p>
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
          <div class="stat-card">
            <span class="stat-value">{{ stats()?.walks }}</span>
            <span class="stat-label">Promenades</span>
          </div>
          <div class="stat-card accent">
            <span class="stat-value">{{ pendingDogs().length }}</span>
            <span class="stat-label">Dossiers en attente</span>
          </div>
        </div>
      }

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
                  <span>{{ dog.breed }} · {{ dog.dossierStatus }}</span>
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
  protected readonly loadingPending = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDashboard();
    this.loadPending();
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
}
