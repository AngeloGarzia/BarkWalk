import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CityPickerComponent } from '../shared/city-picker/city-picker.component';
import { AuthService } from '../core/auth.service';
import { City } from '../shared/city.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CityPickerComponent],
  template: `
    <div class="shell">
      <!-- Header -->
      <header class="site-header">
        <div class="header-inner">
          <a routerLink="/" class="brand">
            <span class="brand-logo">🐾</span>
            <span class="brand-name">BarkWalk</span>
          </a>

          <div class="header-city">
            <app-city-picker (citySelected)="onCitySelected($event)" />
          </div>

          <nav class="main-nav" role="navigation" aria-label="Navigation principale">
            <a routerLink="/catalog" routerLinkActive="active">Promenades</a>
            @if (auth.loggedIn()) {
              <a routerLink="/dogs" routerLinkActive="active">Mes chiens</a>
              <a routerLink="/profile" routerLinkActive="active">Profil</a>
              <button class="nav-logout" (click)="logout()">Déconnexion</button>
            } @else {
              <a routerLink="/login" routerLinkActive="active">Connexion</a>
              <a routerLink="/register" routerLinkActive="active" class="nav-cta">Inscription</a>
            }
          </nav>

          <!-- Mobile menu toggle -->
          <button
            class="menu-toggle"
            (click)="menuOpen.set(!menuOpen())"
            [attr.aria-expanded]="menuOpen()"
            aria-label="Menu"
          >
            <span></span><span></span><span></span>
          </button>
        </div>

        <!-- Mobile nav -->
        @if (menuOpen()) {
          <nav class="mobile-nav" role="navigation" aria-label="Navigation mobile">
            <a routerLink="/catalog" routerLinkActive="active" (click)="menuOpen.set(false)">
              🗺️ Promenades
            </a>
            @if (auth.loggedIn()) {
              <a routerLink="/dogs" routerLinkActive="active" (click)="menuOpen.set(false)">
                🐕 Mes chiens
              </a>
              <a routerLink="/profile" routerLinkActive="active" (click)="menuOpen.set(false)">
                👤 Profil
              </a>
              <a routerLink="/walker/today" routerLinkActive="active" (click)="menuOpen.set(false)">
                📅 Promenades du jour
              </a>
              <a routerLink="/admin" routerLinkActive="active" (click)="menuOpen.set(false)">
                ⚙️ Admin
              </a>
              <button class="mobile-logout" (click)="logout()">Déconnexion</button>
            } @else {
              <a routerLink="/login" routerLinkActive="active" (click)="menuOpen.set(false)">
                Connexion
              </a>
              <a routerLink="/register" routerLinkActive="active" (click)="menuOpen.set(false)">
                Inscription
              </a>
            }
          </nav>
        }
      </header>

      <!-- City chip -->
      @if (selectedCity()) {
        <div class="city-banner">
          <span>📍 {{ selectedCity()?.name }} ({{ selectedCity()?.postalCode }})</span>
          <button (click)="clearCity()" aria-label="Changer de ville">✕</button>
        </div>
      }

      <!-- Main content -->
      <main class="main-content">
        <router-outlet />
      </main>

      <!-- Footer -->
      <footer class="site-footer">
        <p>&copy; 2026 BarkWalk · Promenades canines de confiance</p>
      </footer>
    </div>
  `,
  styles: [
    `
      .shell {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
      }

      /* ── Header ── */
      .site-header {
        background: white;
        border-bottom: 1px solid var(--line);
        position: sticky;
        top: 0;
        z-index: 50;
      }
      .header-inner {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.75rem 1.25rem;
        max-width: 1200px;
        margin: 0 auto;
        width: 100%;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        text-decoration: none;
        flex-shrink: 0;
      }
      .brand-logo {
        font-size: 1.3rem;
      }
      .brand-name {
        font-family: var(--font-display);
        font-size: 1.3rem;
        font-weight: 700;
        color: var(--accent);
        letter-spacing: -0.01em;
      }
      .header-city {
        flex: 1;
        max-width: 280px;
        margin-left: auto;
      }
      .main-nav {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-shrink: 0;
      }
      .main-nav a {
        padding: 0.4rem 0.75rem;
        border-radius: 6px;
        text-decoration: none;
        font-size: 0.9rem;
        font-weight: 600;
        color: var(--muted);
        transition: color 0.15s, background 0.15s;
      }
      .main-nav a:hover,
      .main-nav a.active {
        color: var(--accent);
        background: rgba(47, 107, 79, 0.06);
      }
      .nav-cta {
        background: var(--accent) !important;
        color: white !important;
        margin-left: 0.25rem;
      }
      .nav-cta:hover {
        opacity: 0.88;
      }
      .nav-logout {
        background: transparent;
        border: 1px solid var(--line);
        border-radius: 6px;
        padding: 0.4rem 0.75rem;
        font-size: 0.9rem;
        font-weight: 600;
        color: var(--muted);
        cursor: pointer;
        margin-left: 0.25rem;
        transition: border-color 0.15s, color 0.15s;
      }
      .nav-logout:hover {
        border-color: #e53e3e;
        color: #e53e3e;
      }

      /* ── Mobile menu toggle ── */
      .menu-toggle {
        display: none;
        flex-direction: column;
        justify-content: center;
        gap: 5px;
        background: transparent;
        border: none;
        cursor: pointer;
        padding: 4px;
        margin-left: auto;
      }
      .menu-toggle span {
        display: block;
        width: 22px;
        height: 2px;
        background: var(--ink);
        border-radius: 2px;
      }

      /* ── Mobile nav ── */
      .mobile-nav {
        display: flex;
        flex-direction: column;
        padding: 0.5rem 1.25rem 1rem;
        border-top: 1px solid var(--line);
        background: white;
        gap: 0.25rem;
      }
      .mobile-nav a {
        padding: 0.6rem 0.75rem;
        border-radius: 8px;
        text-decoration: none;
        font-size: 0.95rem;
        font-weight: 600;
        color: var(--muted);
        transition: background 0.15s;
      }
      .mobile-nav a:hover,
      .mobile-nav a.active {
        background: var(--bg);
        color: var(--accent);
      }
      .mobile-logout {
        background: transparent;
        border: 1px solid #e53e3e;
        border-radius: 8px;
        padding: 0.6rem 0.75rem;
        font-size: 0.95rem;
        font-weight: 600;
        color: #e53e3e;
        cursor: pointer;
        text-align: left;
        margin-top: 0.25rem;
      }

      /* ── City banner ── */
      .city-banner {
        background: rgba(47, 107, 79, 0.08);
        border-bottom: 1px solid rgba(47, 107, 79, 0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.75rem;
        padding: 0.4rem 1rem;
        font-size: 0.85rem;
        color: var(--accent);
        font-weight: 600;
      }
      .city-banner button {
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--accent);
        font-size: 0.9rem;
        line-height: 1;
        padding: 0 2px;
      }

      /* ── Main content ── */
      .main-content {
        flex: 1;
        min-height: 0;
      }

      /* ── Footer ── */
      .site-footer {
        border-top: 1px solid var(--line);
        padding: 1.25rem;
        text-align: center;
        font-size: 0.85rem;
        color: var(--muted);
        background: white;
      }

      /* ── Responsive ── */
      @media (max-width: 680px) {
        .main-nav {
          display: none;
        }
        .header-city {
          max-width: 160px;
        }
        .menu-toggle {
          display: flex;
        }
      }
    `,
  ],
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly menuOpen = signal(false);
  protected readonly selectedCity = signal<City | null>(null);

  protected onCitySelected(city: City): void {
    this.selectedCity.set(city);
  }

  protected clearCity(): void {
    this.selectedCity.set(null);
  }

  protected logout(): void {
    this.auth.logout();
    this.menuOpen.set(false);
    this.router.navigate(['/']);
  }
}
