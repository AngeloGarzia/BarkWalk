// Ligne participants (pseudos) — affichée seulement si l’API a fourni la liste (admin / orga).
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-walk-participants',
  standalone: true,
  template: `
    @if (names !== null && names !== undefined) {
      <div class="participants" aria-label="Participants">
        <span class="part-label">Participants</span>
        @if (names.length === 0) {
          <span class="part-empty">Aucun inscrit</span>
        } @else {
          <ul class="part-list">
            @for (name of names; track name) {
              <li class="part-pill">{{ formatName(name) }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [
    `
      .participants {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.3rem 0.4rem;
        margin-top: 0.4rem;
      }
      .part-label {
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.02em;
        text-transform: uppercase;
        color: var(--muted);
      }
      .part-empty {
        font-size: 0.78rem;
        color: var(--muted);
      }
      .part-list {
        display: flex;
        flex-wrap: wrap;
        gap: 0.28rem;
        list-style: none;
        margin: 0;
        padding: 0;
      }
      .part-pill {
        font-size: 0.75rem;
        font-weight: 600;
        padding: 0.12rem 0.45rem;
        border-radius: var(--radius-sm);
        background: color-mix(in srgb, var(--forest) 12%, #fff);
        color: var(--forest-deep);
        border: 1px solid color-mix(in srgb, var(--forest) 22%, transparent);
      }
    `,
  ],
})
export class WalkParticipantsComponent {
  /** null/undefined = non autorisé à voir ; [] = aucun participant. */
  @Input() names: string[] | null | undefined;

  protected formatName(name: string): string {
    const trimmed = (name || '').trim();
    if (!trimmed) {
      return '—';
    }
    if (trimmed.startsWith('@') || /\s/.test(trimmed)) {
      return trimmed;
    }
    return `@${trimmed}`;
  }
}
