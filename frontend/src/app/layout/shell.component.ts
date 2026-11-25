// Coquille app : contenu + navigation bas style POC (Accueil / Balades / Messages / Chiens / Profil).
import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <main class="main-content">
        <router-outlet />
      </main>

      <nav class="bottom-nav" role="navigation" aria-label="Navigation principale">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" class="tab">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1v-9.5Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
          </svg>
          <span>Accueil</span>
        </a>

        <a routerLink="/catalog" routerLinkActive="active" class="tab">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="5" width="16" height="15" rx="2" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path d="M8 3v4M16 3v4M4 10h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
          <span>Balades</span>
        </a>

        <a
          [routerLink]="auth.loggedIn() ? '/messaging' : '/login'"
          routerLinkActive="active"
          class="tab"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M5 6h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-4 3v-3H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
          </svg>
          <span>Messages</span>
        </a>

        <a
          [routerLink]="auth.loggedIn() ? '/dogs' : '/login'"
          routerLinkActive="active"
          class="tab"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="7.5" cy="8" r="1.6" fill="currentColor" />
            <circle cx="16.5" cy="8" r="1.6" fill="currentColor" />
            <circle cx="5" cy="12.5" r="1.5" fill="currentColor" />
            <circle cx="19" cy="12.5" r="1.5" fill="currentColor" />
            <ellipse cx="12" cy="15.5" rx="3.2" ry="2.6" fill="currentColor" />
          </svg>
          <span>Mes chiens</span>
        </a>

        <a
          [routerLink]="auth.loggedIn() ? '/profile' : '/login'"
          routerLinkActive="active"
          class="tab"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="9" r="3.2" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path
              d="M5.5 19.5c1.6-3 4-4.5 6.5-4.5s4.9 1.5 6.5 4.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
            />
          </svg>
          <span>Profil</span>
        </a>
      </nav>
    </div>
  `,
  styles: [
    `
      .shell {
        min-height: 100dvh;
        display: flex;
        flex-direction: column;
        background: var(--bg);
      }

      .main-content {
        flex: 1;
        min-height: 0;
        padding-bottom: calc(var(--nav-h) + env(safe-area-inset-bottom, 0px));
      }

      .bottom-nav {
        position: fixed;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 60;
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 0.15rem;
        height: calc(var(--nav-h) + env(safe-area-inset-bottom, 0px));
        padding: 0.45rem 0.35rem calc(0.45rem + env(safe-area-inset-bottom, 0px));
        background: rgba(255, 255, 255, 0.96);
        border-top: 1px solid var(--line);
        backdrop-filter: blur(10px);
        max-width: 32rem;
        margin: 0 auto;
        box-shadow: 0 -6px 24px rgba(31, 61, 34, 0.06);
      }

      .tab {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.2rem;
        color: #8a9186;
        text-decoration: none;
        font-size: 0.68rem;
        font-weight: 600;
        border-radius: 12px;
        transition:
          color 0.15s ease,
          background 0.15s ease;
      }

      .tab svg {
        width: 1.35rem;
        height: 1.35rem;
      }

      .tab.active {
        color: var(--forest);
      }

      .tab:hover {
        color: var(--forest-deep);
        background: var(--forest-soft);
      }

      @media (min-width: 720px) {
        .bottom-nav {
          left: 50%;
          right: auto;
          transform: translateX(-50%);
          width: min(32rem, 100%);
          border-radius: 18px 18px 0 0;
        }
      }
    `,
  ],
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
}
