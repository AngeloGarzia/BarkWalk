import { Component, computed, input } from '@angular/core';
import { difficultyLabel } from '../walk-difficulty';

/** Indicateur visuel difficulté 1–10 (barres). */
@Component({
  selector: 'app-walk-difficulty-bar',
  standalone: true,
  template: `
    @if (level(); as lvl) {
      <div
        class="diff-meter"
        [attr.data-band]="band()"
        [attr.title]="titleText()"
        [attr.aria-label]="titleText()"
        role="img"
      >
        <span class="diff-icon" aria-hidden="true">
          @for (filled of segments(); track $index) {
            <i [class.on]="filled"></i>
          }
        </span>
        <span class="diff-caption">{{ lvl }}/10</span>
      </div>
    }
  `,
  styles: [
    `
      .diff-meter {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        line-height: 1;
        vertical-align: middle;
      }
      .diff-icon {
        display: inline-flex;
        align-items: flex-end;
        gap: 2px;
        height: 12px;
      }
      .diff-icon i {
        display: block;
        width: 3px;
        border-radius: 1px;
        background: color-mix(in srgb, var(--line, #c9c4b8) 85%, transparent);
      }
      .diff-icon i:nth-child(1) {
        height: 4px;
      }
      .diff-icon i:nth-child(2) {
        height: 5px;
      }
      .diff-icon i:nth-child(3) {
        height: 6px;
      }
      .diff-icon i:nth-child(4) {
        height: 7px;
      }
      .diff-icon i:nth-child(5) {
        height: 8px;
      }
      .diff-icon i:nth-child(6) {
        height: 9px;
      }
      .diff-icon i:nth-child(7) {
        height: 10px;
      }
      .diff-icon i:nth-child(8) {
        height: 11px;
      }
      .diff-icon i:nth-child(9),
      .diff-icon i:nth-child(10) {
        height: 12px;
      }
      .diff-icon i.on {
        background: var(--diff-color, var(--forest, #2f5d3a));
      }
      .diff-meter[data-band='easy'] {
        --diff-color: #4a8f5c;
      }
      .diff-meter[data-band='moderate'] {
        --diff-color: #c48a2a;
      }
      .diff-meter[data-band='hard'] {
        --diff-color: #b84a3a;
      }
      .diff-caption {
        font-size: 0.72rem;
        font-weight: 700;
        color: var(--muted, #6b7280);
        letter-spacing: 0.02em;
      }
    `,
  ],
})
export class WalkDifficultyBarComponent {
  readonly level = input<number | null | undefined>(null);

  protected readonly segments = computed(() => {
    const n = this.normalized();
    if (n == null) {
      return [];
    }
    return Array.from({ length: 10 }, (_, i) => i < n);
  });

  protected readonly titleText = computed(() => {
    const n = this.normalized();
    if (n == null) {
      return '';
    }
    return `Difficulté ${n}/10 · ${difficultyLabel(n)}`;
  });

  protected readonly band = computed(() => {
    const n = this.normalized();
    if (n == null) {
      return null;
    }
    if (n <= 4) {
      return 'easy';
    }
    if (n <= 7) {
      return 'moderate';
    }
    return 'hard';
  });

  private normalized(): number | null {
    const raw = this.level();
    if (raw == null || !Number.isFinite(raw) || raw < 1) {
      return null;
    }
    return Math.max(1, Math.min(10, Math.round(raw)));
  }
}
