// Coquille app : contenu + navigation bas style POC (Accueil / Balades / Messages / Chiens / Profil).
import { Component, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AttendanceReminderService } from '../core/attendance-reminder.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <header class="top-brand" role="banner">
        <a routerLink="/" class="brand" aria-label="Bark & Walk — accueil">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <circle cx="7.2" cy="8" r="1.5" fill="currentColor" />
              <circle cx="16.8" cy="8" r="1.5" fill="currentColor" />
              <circle cx="5" cy="12.2" r="1.35" fill="currentColor" />
              <circle cx="19" cy="12.2" r="1.35" fill="currentColor" />
              <ellipse cx="12" cy="15.4" rx="3" ry="2.4" fill="currentColor" />
            </svg>
          </span>
          <span class="brand-wordmark">BARK &amp; WALK</span>
        </a>
      </header>

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

        <div class="tab-wrap walks-wrap" (click)="$event.stopPropagation()">
          @if (walksMenuOpen()) {
            <div class="walks-menu" role="menu" aria-label="Menu balades">
              <a
                routerLink="/catalog"
                role="menuitem"
                class="walks-menu-item"
                [class.active]="isCatalogRoute()"
                (click)="closeWalksMenu()"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M4 7h16M4 12h16M4 17h10"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                  />
                </svg>
                <span>
                  <strong>Catalogue</strong>
                  <em>Balades à venir près de chez vous</em>
                </span>
              </a>
              @if (auth.canAccessCalendar()) {
                <a
                  routerLink="/calendar"
                  role="menuitem"
                  class="walks-menu-item"
                  [class.active]="isCalendarRoute()"
                  (click)="closeWalksMenu()"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <rect
                      x="4"
                      y="5"
                      width="16"
                      height="15"
                      rx="2"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                    />
                    <path
                      d="M8 3v4M16 3v4M4 10h16"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                  </svg>
                  <span>
                    <strong>Agenda</strong>
                    <em>Planning passé et à venir</em>
                  </span>
                </a>
              }
            </div>
          }

          <button
            type="button"
            class="tab"
            [class.active]="isWalksSectionActive()"
            [class.needs-attention]="attendance.pendingCount() > 0"
            [attr.aria-expanded]="walksMenuOpen()"
            aria-haspopup="menu"
            [attr.aria-label]="
              attendance.pendingCount() > 0 ? 'Balades — pointage à valider' : 'Balades'
            "
            (click)="onWalksTabClick($event)"
          >
            <span class="tab-icon-wrap">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="4" y="5" width="16" height="15" rx="2" fill="none" stroke="currentColor" stroke-width="1.8" />
                <path
                  d="M8 3v4M16 3v4M4 10h16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
              </svg>
              @if (attendance.pendingCount(); as n) {
                @if (n > 0) {
                  <span class="attn-dot" aria-hidden="true">{{ n > 9 ? '9+' : n }}</span>
                }
              }
            </span>
            <span [class.attn-label]="attendance.pendingCount() > 0">Balades</span>
          </button>
        </div>

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

      .top-brand {
        position: sticky;
        top: 0;
        z-index: 50;
        display: flex;
        align-items: center;
        min-height: calc(var(--header-h) + env(safe-area-inset-top, 0px));
        padding: calc(0.45rem + env(safe-area-inset-top, 0px)) 1rem 0.45rem;
        background: color-mix(in srgb, var(--bg) 92%, transparent);
        border-bottom: 1px solid color-mix(in srgb, var(--line) 80%, transparent);
        backdrop-filter: blur(10px);
      }

      .brand {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        text-decoration: none;
        color: var(--forest);
      }

      .brand:hover {
        color: var(--forest-deep);
      }

      .brand-mark {
        width: 1.55rem;
        height: 1.55rem;
        display: grid;
        place-items: center;
        color: inherit;
      }

      .brand-mark svg {
        width: 100%;
        height: 100%;
      }

      .brand-wordmark {
        font-size: 1.05rem;
        letter-spacing: 0.04em;
        color: inherit;
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

      .tab-wrap {
        position: relative;
        display: flex;
        align-items: stretch;
        justify-content: center;
      }

      .walks-menu {
        position: absolute;
        left: 50%;
        bottom: calc(100% + 0.55rem);
        transform: translateX(-50%);
        width: min(18.5rem, calc(100vw - 1.5rem));
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        padding: 0.4rem;
        border-radius: 16px;
        border: 1px solid var(--line);
        background: #fff;
        box-shadow: 0 12px 32px rgba(31, 61, 34, 0.14);
        z-index: 70;
      }

      .walks-menu::after {
        content: '';
        position: absolute;
        left: 50%;
        bottom: -6px;
        width: 12px;
        height: 12px;
        transform: translateX(-50%) rotate(45deg);
        background: #fff;
        border-right: 1px solid var(--line);
        border-bottom: 1px solid var(--line);
      }

      .walks-menu-item {
        display: grid;
        grid-template-columns: 1.4rem 1fr;
        gap: 0.65rem;
        align-items: start;
        padding: 0.7rem 0.75rem;
        border-radius: 12px;
        text-decoration: none;
        color: var(--ink);
      }

      .walks-menu-item svg {
        width: 1.25rem;
        height: 1.25rem;
        margin-top: 0.1rem;
        color: var(--forest);
      }

      .walks-menu-item strong {
        display: block;
        font-size: 0.88rem;
        font-weight: 800;
      }

      .walks-menu-item em {
        display: block;
        margin-top: 0.1rem;
        font-style: normal;
        font-size: 0.72rem;
        color: var(--muted);
        line-height: 1.25;
      }

      .walks-menu-item.active,
      .walks-menu-item:hover {
        background: var(--forest-soft);
      }

      .tab {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.2rem;
        width: 100%;
        color: #8a9186;
        text-decoration: none;
        font-size: 0.68rem;
        font-weight: 600;
        border-radius: 12px;
        border: 0;
        background: transparent;
        cursor: pointer;
        font-family: inherit;
        padding: 0;
        transition:
          color 0.15s ease,
          background 0.15s ease;
      }

      .tab svg {
        width: 1.35rem;
        height: 1.35rem;
      }

      .tab-icon-wrap {
        position: relative;
        display: inline-flex;
        line-height: 0;
      }

      .attn-dot {
        position: absolute;
        top: -0.35rem;
        right: -0.55rem;
        min-width: 1rem;
        height: 1rem;
        padding: 0 0.2rem;
        border-radius: 999px;
        background: var(--cta);
        color: #fff;
        font-size: 0.58rem;
        font-weight: 800;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 1;
        box-shadow: 0 0 0 2px #fff;
      }

      .tab.needs-attention {
        color: var(--cta);
        animation: nav-attn-blink 1.1s ease-in-out infinite;
      }

      .tab.needs-attention .attn-label {
        font-weight: 800;
      }

      @keyframes nav-attn-blink {
        0%,
        100% {
          opacity: 1;
          filter: brightness(1);
        }
        50% {
          opacity: 0.45;
          filter: brightness(1.15);
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .tab.needs-attention {
          animation: none;
          outline: 2px solid var(--cta);
          outline-offset: 1px;
        }
      }

      .tab.active {
        color: var(--forest);
      }

      .tab.needs-attention.active {
        color: var(--cta);
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
  protected readonly attendance = inject(AttendanceReminderService);
  private readonly router = inject(Router);

  protected readonly walksMenuOpen = signal(false);
  private readonly currentUrl = signal(this.router.url);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.currentUrl.set(e.urlAfterRedirects);
        this.walksMenuOpen.set(false);
      });
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.walksMenuOpen()) {
      this.walksMenuOpen.set(false);
    }
  }

  protected onWalksTabClick(event: MouseEvent): void {
    event.stopPropagation();
    if (!this.auth.canAccessCalendar()) {
      void this.router.navigateByUrl('/catalog');
      return;
    }
    this.walksMenuOpen.update((open) => !open);
  }

  protected closeWalksMenu(): void {
    this.walksMenuOpen.set(false);
  }

  protected isCatalogRoute(): boolean {
    return this.currentUrl().startsWith('/catalog');
  }

  protected isCalendarRoute(): boolean {
    return this.currentUrl().startsWith('/calendar');
  }

  protected isWalksSectionActive(): boolean {
    return this.isCatalogRoute() || this.isCalendarRoute() || this.walksMenuOpen();
  }
}
