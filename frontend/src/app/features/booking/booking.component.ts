// Parcours de réservation.
import { Component } from '@angular/core';

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [],
  template: `
    <div class="booking-page">
      <header class="page-header">
        <h1>Mes réservations</h1>
        <p class="subtitle">Suivez l&apos;état de vos réservations de promenades.</p>
      </header>
      <div class="coming-soon">
        <span class="icon">📅</span>
        <p>La gestion des réservations sera disponible prochainement.</p>
      </div>
    </div>
  `,
  styles: [
    `
      .booking-page {
        max-width: 760px;
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
      .coming-soon {
        text-align: center;
        padding: 4rem 1rem;
        color: var(--muted);
      }
      .icon {
        font-size: 3rem;
        display: block;
        margin-bottom: 1rem;
      }
    `,
  ],
})
export class BookingComponent {}
