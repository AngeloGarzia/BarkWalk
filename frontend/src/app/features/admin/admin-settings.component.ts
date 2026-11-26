// Page admin : édition des paramètres métier (table settings).
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface SettingRow {
  key: string;
  value: string;
  description: string;
  updatedAt: string | null;
}

interface EditableSetting extends SettingRow {
  draft: string;
  saving: boolean;
  savedAt: number | null;
  error: string | null;
}

const SECTIONS: { id: string; title: string; keys: string[] }[] = [
  {
    id: 'booking',
    title: 'Annulation & paiement',
    keys: ['cancellation_free_hours', 'pending_payment_minutes'],
  },
  {
    id: 'waitlist',
    title: "Liste d'attente",
    keys: ['waitlist_offer_hours'],
  },
  {
    id: 'community',
    title: 'Communautaire & organisateurs',
    keys: [
      'community_walks_threshold',
      'community_capacity_max',
      'org_min_rating',
      'org_max_reports',
      'org_min_reviews',
    ],
  },
  {
    id: 'finance',
    title: 'Commission & avis',
    keys: ['indep_commission_percent', 'review_window_days'],
  },
  {
    id: 'docs',
    title: 'Rappels documents',
    keys: ['doc_reminder_j30_days', 'doc_reminder_j7_days'],
  },
];

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="admin-page">
      <header class="page-header">
        <div class="header-row">
          <div>
            <h1>Paramètres</h1>
            <p class="subtitle">Réglages métier de l’application (règles R4–R16).</p>
          </div>
          <a routerLink="/admin" class="btn-ghost">Retour admin</a>
        </div>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
      @if (loading()) {
        <p class="muted">Chargement…</p>
      } @else {
        @for (section of sections(); track section.id) {
          <section class="panel" [attr.aria-labelledby]="'sec-' + section.id">
            <h2 [id]="'sec-' + section.id">{{ section.title }}</h2>
            <ul class="settings-list">
              @for (row of section.rows; track row.key) {
                <li class="setting-row">
                  <div class="setting-meta">
                    <strong>{{ row.description || row.key }}</strong>
                    <code>{{ row.key }}</code>
                  </div>
                  <div class="setting-edit">
                    <input
                      type="number"
                      [ngModel]="row.draft"
                      (ngModelChange)="onDraft(row.key, $event)"
                      [attr.aria-label]="row.key"
                      [disabled]="row.saving"
                    />
                    <button
                      type="button"
                      class="btn-save"
                      (click)="save(row)"
                      [disabled]="row.saving || row.draft === row.value"
                    >
                      {{ row.saving ? '…' : 'Enregistrer' }}
                    </button>
                  </div>
                  @if (row.error) {
                    <p class="row-msg error">{{ row.error }}</p>
                  } @else if (row.savedAt) {
                    <p class="row-msg ok">Enregistré</p>
                  }
                </li>
              }
            </ul>
          </section>
        }
      }
    </div>
  `,
  styles: [
    `
      .admin-page {
        max-width: 900px;
        margin: 0 auto;
        padding: 1.5rem 1rem 2.5rem;
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
      .panel {
        background: white;
        border-radius: 12px;
        padding: 1.25rem 1.5rem;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
        margin-bottom: 1rem;
      }
      .panel h2 {
        font-family: var(--font-display);
        font-size: 1.05rem;
        margin: 0 0 1rem;
      }
      .settings-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
      }
      .setting-row {
        display: grid;
        gap: 0.5rem;
        padding: 0.85rem 1rem;
        background: var(--bg);
        border-radius: 8px;
      }
      .setting-meta {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .setting-meta code {
        font-size: 0.75rem;
        color: var(--muted);
      }
      .setting-edit {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        flex-wrap: wrap;
      }
      .setting-edit input {
        width: 7rem;
        padding: 0.4rem 0.6rem;
        border: 1px solid var(--line);
        border-radius: 8px;
        font-size: 0.95rem;
        background: white;
      }
      .btn-save {
        background: var(--accent);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 0.4rem 0.9rem;
        font-size: 0.85rem;
        font-weight: 600;
        cursor: pointer;
      }
      .btn-save:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn-ghost {
        background: transparent;
        border: 1px solid var(--line);
        border-radius: 8px;
        padding: 0.4rem 0.9rem;
        font-size: 0.88rem;
        text-decoration: none;
        color: var(--muted);
      }
      .btn-ghost:hover {
        border-color: var(--accent);
        color: var(--accent);
      }
      .row-msg {
        margin: 0;
        font-size: 0.8rem;
      }
      .row-msg.ok {
        color: #276749;
      }
      .row-msg.error {
        color: #9b1c1c;
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
export class AdminSettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly rows = signal<EditableSetting[]>([]);

  protected readonly sections = computed(() => {
    const byKey = new Map(this.rows().map((r) => [r.key, r]));
    const known = new Set(SECTIONS.flatMap((s) => s.keys));
    const built = SECTIONS.map((s) => ({
      id: s.id,
      title: s.title,
      rows: s.keys.map((k) => byKey.get(k)).filter((r): r is EditableSetting => !!r),
    })).filter((s) => s.rows.length > 0);

    const orphans = this.rows().filter((r) => !known.has(r.key));
    if (orphans.length) {
      built.push({ id: 'other', title: 'Autres', rows: orphans });
    }
    return built;
  });

  ngOnInit(): void {
    this.load();
  }

  protected onDraft(key: string, value: string): void {
    this.rows.update((list) =>
      list.map((r) => (r.key === key ? { ...r, draft: value, savedAt: null, error: null } : r)),
    );
  }

  protected save(row: EditableSetting): void {
    this.patchRow(row.key, { saving: true, error: null });
    this.http.put<SettingRow>(`/api/admin/settings/${row.key}`, { value: row.draft }).subscribe({
      next: (updated) => {
        this.patchRow(row.key, {
          value: updated.value,
          draft: updated.value,
          description: updated.description,
          updatedAt: updated.updatedAt,
          saving: false,
          savedAt: Date.now(),
          error: null,
        });
      },
      error: (err: HttpErrorResponse) => {
        const msg =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : typeof err.error?.title === 'string'
              ? err.error.title
              : 'Échec de la mise à jour.';
        this.patchRow(row.key, { saving: false, error: msg });
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<SettingRow[]>('/api/admin/settings').subscribe({
      next: (list) => {
        this.rows.set(
          list.map((s) => ({
            ...s,
            draft: s.value,
            saving: false,
            savedAt: null,
            error: null,
          })),
        );
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les paramètres.');
        this.loading.set(false);
      },
    });
  }

  private patchRow(key: string, patch: Partial<EditableSetting>): void {
    this.rows.update((list) => list.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }
}
