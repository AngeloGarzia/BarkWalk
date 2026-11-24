import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="hero-page">
      <section class="hero-content">
        <p class="eyebrow">🐾 Promenades canines</p>
        <h1 class="display">BarkWalk</h1>
        <p class="lead">
          Trouvez le promeneur parfait pour votre chien, près de chez vous. Réservez en quelques
          clics.
        </p>
        <div class="hero-actions">
          <a routerLink="/catalog" class="btn-primary">Voir les promenades</a>
          <a routerLink="/register" class="btn-ghost">Devenir promeneur</a>
        </div>
      </section>

      <section class="features-grid">
        <div class="feature-card">
          <span class="icon">🗺️</span>
          <h3>Par ville</h3>
          <p>Filtrez les promeneurs disponibles dans votre quartier.</p>
        </div>
        <div class="feature-card">
          <span class="icon">⭐</span>
          <h3>Promeneurs vérifiés</h3>
          <p>Chaque promeneur est validé par notre équipe.</p>
        </div>
        <div class="feature-card">
          <span class="icon">📅</span>
          <h3>Réservation simple</h3>
          <p>Réservez et payez directement depuis l&apos;application.</p>
        </div>
      </section>
    </div>
  `,
  styles: [
    `
      .hero-page {
        padding: 2rem 1rem 4rem;
      }
      .hero-content {
        max-width: 600px;
        margin: 0 auto 3rem;
        text-align: center;
        padding-top: 2rem;
      }
      .eyebrow {
        font-size: 0.9rem;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--accent);
        font-weight: 600;
        margin-bottom: 0.5rem;
      }
      .display {
        font-family: var(--font-display);
        font-size: clamp(2.5rem, 7vw, 4rem);
        color: var(--ink);
        margin: 0 0 1rem;
        line-height: 1.1;
      }
      .lead {
        font-size: 1.1rem;
        color: var(--muted);
        margin-bottom: 2rem;
        line-height: 1.6;
      }
      .hero-actions {
        display: flex;
        gap: 1rem;
        justify-content: center;
        flex-wrap: wrap;
      }
      .btn-primary {
        background: var(--accent);
        color: white;
        padding: 0.8rem 1.75rem;
        border-radius: 8px;
        text-decoration: none;
        font-weight: 600;
        font-size: 1rem;
        transition: opacity 0.15s;
      }
      .btn-primary:hover {
        opacity: 0.88;
      }
      .btn-ghost {
        border: 2px solid var(--accent);
        color: var(--accent);
        padding: 0.8rem 1.75rem;
        border-radius: 8px;
        text-decoration: none;
        font-weight: 600;
        font-size: 1rem;
        transition: background 0.15s;
      }
      .btn-ghost:hover {
        background: rgba(47, 107, 79, 0.06);
      }
      .features-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 1.25rem;
        max-width: 760px;
        margin: 0 auto;
      }
      .feature-card {
        background: white;
        border-radius: 12px;
        padding: 1.5rem;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
        text-align: center;
      }
      .icon {
        font-size: 2rem;
        display: block;
        margin-bottom: 0.75rem;
      }
      .feature-card h3 {
        margin: 0 0 0.4rem;
        font-family: var(--font-display);
        font-size: 1.1rem;
      }
      .feature-card p {
        margin: 0;
        color: var(--muted);
        font-size: 0.9rem;
        line-height: 1.5;
      }
    `,
  ],
})
export class HomeComponent {}
