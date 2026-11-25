// Agenda du jour pour l'encadrant.
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface TodayWalk {
  id: number;
  title: string;
  startsAt: string;
  durationMinutes: number;
  address: string;
  status: string;
  dogCount: number;
}

@Component({
  selector: 'app-walker-today',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="walker-page">
      <header class="page-header">
        <h1>Mes promenades du jour</h1>
        <p class="subtitle">Vos missions planifiées pour aujourd&apos;hui.</p>
      </header>

      @if (loading()) {
        <p class="muted">Chargement…</p>
      } @else if (walks().length === 0) {
        <div class="empty-state">
          <p>Aucune promenade planifiée aujourd&apos;hui.</p>
        </div>
      } @else {
        <ul class="walk-list">
          @for (walk of walks(); track walk.id) {
            <li class="walk-item">
              <div class="time-col">
                <span class="time">{{ walk.startsAt | date: 'HH:mm' }}</span>
                <span class="time-end">{{ walk.durationMinutes }} min</span>
              </div>
              <div class="walk-details">
                <strong>{{ walk.title }}</strong>
                <span>{{ walk.address }}</span>
                <span class="muted">{{ walk.dogCount }} chien(s)</span>
              </div>
              <span class="status" [class]="'status-' + walk.status.toLowerCase()">
                {{ statusLabel(walk.status) }}
              </span>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [
    `
      .walker-page {
        max-width: 760px;
        margin: 0 auto;
        padding: 1.5rem 1rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
      }
      .subtitle,
      .muted {
        color: var(--muted);
      }
      .walk-list {
        list-style: none;
        margin: 1rem 0 0;
        padding: 0;
        display: grid;
        gap: 0.75rem;
      }
      .walk-item {
        display: grid;
        grid-template-columns: 70px 1fr auto;
        gap: 1rem;
        align-items: center;
        background: #fff;
        border: 1px solid var(--line);
        border-radius: 12px;
        padding: 1rem;
      }
      .time-col {
        display: grid;
        gap: 0.2rem;
      }
      .time {
        font-weight: 700;
        color: var(--accent);
      }
      .walk-details {
        display: grid;
        gap: 0.2rem;
      }
      .status {
        font-size: 0.75rem;
        font-weight: 600;
        padding: 0.25rem 0.55rem;
        border-radius: 999px;
        background: #ebf4f1;
        color: var(--accent);
      }
      .empty-state {
        margin-top: 1.5rem;
        color: var(--muted);
      }
    `,
  ],
})
export class WalkerTodayComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly walks = signal<TodayWalk[]>([]);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.loading.set(true);
    this.http.get<TodayWalk[]>('/api/walkers/today').subscribe({
      next: (walks) => {
        this.walks.set(walks);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      PUBLISHED: 'Planifiée',
      COMPLETED: 'Terminée',
      CANCELLED: 'Annulée',
    };
    return labels[status] ?? status;
  }
}
