import { Component } from '@angular/core';

@Component({
  selector: 'app-messaging',
  standalone: true,
  imports: [],
  template: `
    <div class="messaging-page">
      <header class="page-header">
        <h1>Messages</h1>
        <p class="subtitle">Vos conversations avec les promeneurs et clients.</p>
      </header>
      <div class="coming-soon">
        <span class="icon">💬</span>
        <p>La messagerie sera disponible prochainement.</p>
      </div>
    </div>
  `,
  styles: [
    `
      .messaging-page {
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
export class MessagingComponent {}
