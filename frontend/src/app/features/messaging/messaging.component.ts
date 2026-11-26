// Messagerie R13 : fils WALK + DIRECT, envoi, lecture, blocage.
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';

interface MemberSummary {
  userId: number;
  displayName: string;
}

interface ConversationRow {
  id: number;
  conversationType: string;
  walkId?: number | null;
  title: string;
  lastMessagePreview?: string | null;
  lastMessageAt?: string | null;
  unreadCount: number;
  createdAt: string;
  archivedAt?: string | null;
  members: MemberSummary[];
}

interface MessageRow {
  id: number;
  conversationId: number;
  senderId: number;
  senderDisplayName: string;
  body: string;
  createdAt: string;
  readAt?: string | null;
}

@Component({
  selector: 'app-messaging',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="messaging-page">
      <header class="page-header">
        <h1>Messages</h1>
        <p class="subtitle">Fils de balade et messages privés (règles club R13).</p>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
      @if (info()) {
        <p class="banner info" role="status">{{ info() }}</p>
      }

      <div class="layout" [class.has-thread]="selectedId() != null">
        <aside class="inbox">
          <div class="inbox-head">
            <h2>Conversations</h2>
            <button type="button" class="btn-ghost" (click)="loadConversations()" [disabled]="loadingList()">
              Actualiser
            </button>
          </div>

          @if (loadingList()) {
            <p class="muted">Chargement…</p>
          } @else if (conversations().length === 0) {
            <p class="muted empty">
              Aucun fil pour l’instant. Un fil de groupe apparaît quand une balade est créée ; vous y
              entrez à la confirmation de réservation.
            </p>
          } @else {
            <ul class="thread-list">
              @for (c of conversations(); track c.id) {
                <li>
                  <button
                    type="button"
                    class="thread-item"
                    [class.active]="selectedId() === c.id"
                    [class.unread]="c.unreadCount > 0"
                    (click)="openConversation(c)"
                  >
                    <div class="thread-top">
                      <span class="type">{{ typeLabel(c.conversationType) }}</span>
                      @if (c.unreadCount > 0) {
                        <span class="badge">{{ c.unreadCount }}</span>
                      }
                      @if (c.archivedAt) {
                        <span class="archived">Archivé</span>
                      }
                    </div>
                    <strong class="title">{{ c.title }}</strong>
                    <p class="preview">{{ c.lastMessagePreview || 'Aucun message' }}</p>
                    @if (c.lastMessageAt) {
                      <time>{{ c.lastMessageAt | date: 'short' }}</time>
                    }
                  </button>
                </li>
              }
            </ul>
          }
        </aside>

        <section class="thread">
          @if (selected(); as conv) {
            <header class="thread-head">
              <button type="button" class="back" (click)="closeThread()">←</button>
              <div class="thread-titles">
                <h2>{{ conv.title }}</h2>
                <p class="meta">
                  {{ typeLabel(conv.conversationType) }}
                  · {{ conv.members.length }} participant{{ conv.members.length > 1 ? 's' : '' }}
                  @if (conv.archivedAt) {
                    · archivé
                  }
                </p>
              </div>
            </header>

            @if (conv.conversationType === 'WALK' || conv.conversationType === 'DIRECT') {
              <div class="members">
                @for (m of peers(conv); track m.userId) {
                  <div class="member-row">
                    <span>{{ m.displayName }}</span>
                    <div class="member-actions">
                      @if (conv.conversationType === 'WALK') {
                        <button type="button" class="linkish" (click)="startDirect(m.userId)">
                          Message privé
                        </button>
                      }
                      <button type="button" class="linkish danger" (click)="blockUser(m.userId, m.displayName)">
                        Bloquer
                      </button>
                    </div>
                  </div>
                }
              </div>
            }

            <div class="messages" role="log" aria-live="polite">
              @if (loadingMessages()) {
                <p class="muted">Chargement des messages…</p>
              } @else if (messages().length === 0) {
                <p class="muted">Soyez le premier à écrire.</p>
              } @else {
                @for (msg of messages(); track msg.id) {
                  <article class="bubble" [class.mine]="msg.senderId === myUserId()">
                    <header>
                      <strong>{{ msg.senderDisplayName }}</strong>
                      <time>{{ msg.createdAt | date: 'short' }}</time>
                    </header>
                    <p>{{ msg.body }}</p>
                  </article>
                }
              }
            </div>

            @if (conv.archivedAt) {
              <p class="banner warn">Ce fil est archivé — plus d’envoi possible.</p>
            } @else {
              <form class="composer" (ngSubmit)="send()">
                <label class="sr-only" for="msg-body">Message</label>
                <textarea
                  id="msg-body"
                  name="body"
                  rows="3"
                  maxlength="2000"
                  [(ngModel)]="draft"
                  placeholder="Écrire un message…"
                  [disabled]="sending()"
                ></textarea>
                <button type="submit" class="btn-primary" [disabled]="sending() || !draft.trim()">
                  {{ sending() ? 'Envoi…' : 'Envoyer' }}
                </button>
              </form>
            }
          } @else {
            <div class="placeholder">
              <p>Sélectionnez une conversation pour lire et répondre.</p>
            </div>
          }
        </section>
      </div>
    </div>
  `,
  styles: [
    `
      .messaging-page {
        max-width: 56rem;
        margin: 0 auto;
        padding: 1rem 1rem 2.5rem;
      }
      .page-header {
        margin-bottom: 1rem;
      }
      h1 {
        margin: 0 0 0.2rem;
        font-family: var(--font-display);
        font-size: 1.55rem;
        color: var(--forest-deep);
      }
      .subtitle {
        margin: 0;
        color: var(--muted);
        font-size: 0.9rem;
      }
      .banner {
        padding: 0.65rem 0.8rem;
        border-radius: var(--radius-sm);
        margin: 0 0 0.75rem;
        font-size: 0.88rem;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
      }
      .banner.info {
        background: #e8f4ec;
        color: var(--forest-deep);
      }
      .banner.warn {
        background: #fff4e5;
        color: #8a5a12;
      }
      .layout {
        display: grid;
        gap: 0.85rem;
      }
      @media (min-width: 780px) {
        .layout {
          grid-template-columns: minmax(14rem, 18rem) 1fr;
          min-height: 28rem;
        }
      }
      .inbox,
      .thread {
        background: var(--surface, #fff);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        overflow: hidden;
      }
      .inbox-head,
      .thread-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0.75rem 0.85rem;
        border-bottom: 1px solid var(--line);
      }
      .inbox-head h2,
      .thread-titles h2 {
        margin: 0;
        font-size: 1rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .thread-list {
        list-style: none;
        margin: 0;
        padding: 0;
        max-height: 70vh;
        overflow: auto;
      }
      .thread-item {
        width: 100%;
        text-align: left;
        border: 0;
        border-bottom: 1px solid var(--line);
        background: transparent;
        padding: 0.75rem 0.85rem;
        cursor: pointer;
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        font: inherit;
        color: inherit;
      }
      .thread-item:hover,
      .thread-item.active {
        background: var(--bg);
      }
      .thread-item.unread .title {
        font-weight: 800;
      }
      .thread-top {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.72rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--muted);
        font-weight: 700;
      }
      .badge {
        background: var(--forest, #2f5d3a);
        color: #fff;
        border-radius: 999px;
        padding: 0.05rem 0.4rem;
        font-size: 0.68rem;
      }
      .archived {
        color: #8a5a12;
      }
      .title {
        font-size: 0.92rem;
      }
      .preview {
        margin: 0;
        font-size: 0.82rem;
        color: var(--muted);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      time {
        font-size: 0.72rem;
        color: var(--muted);
      }
      .empty,
      .muted {
        color: var(--muted);
        font-size: 0.88rem;
        padding: 0.85rem;
      }
      .thread {
        display: flex;
        flex-direction: column;
        min-height: 22rem;
      }
      .back {
        border: 0;
        background: transparent;
        font: inherit;
        font-size: 1.1rem;
        cursor: pointer;
        color: var(--muted);
        padding: 0.2rem 0.35rem;
      }
      @media (min-width: 780px) {
        .back {
          display: none;
        }
      }
      .thread-titles .meta {
        margin: 0.15rem 0 0;
        font-size: 0.78rem;
        color: var(--muted);
      }
      .members {
        border-bottom: 1px solid var(--line);
        padding: 0.45rem 0.85rem;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        max-height: 7rem;
        overflow: auto;
      }
      .member-row {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
        align-items: center;
        font-size: 0.82rem;
      }
      .member-actions {
        display: flex;
        gap: 0.55rem;
      }
      .linkish {
        border: 0;
        background: transparent;
        color: var(--forest-deep);
        font: inherit;
        font-size: 0.78rem;
        font-weight: 700;
        cursor: pointer;
        padding: 0;
        text-decoration: underline;
      }
      .linkish.danger {
        color: var(--danger);
      }
      .messages {
        flex: 1;
        padding: 0.85rem;
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
        overflow: auto;
        background: linear-gradient(180deg, var(--bg) 0%, transparent 8rem);
      }
      .bubble {
        max-width: min(28rem, 92%);
        background: var(--surface, #fff);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 0.55rem 0.7rem;
        align-self: flex-start;
      }
      .bubble.mine {
        align-self: flex-end;
        background: #e8f4ec;
        border-color: #c9dfd0;
      }
      .bubble header {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        margin-bottom: 0.25rem;
        font-size: 0.75rem;
      }
      .bubble p {
        margin: 0;
        white-space: pre-wrap;
        font-size: 0.9rem;
        line-height: 1.35;
      }
      .composer {
        display: grid;
        gap: 0.5rem;
        padding: 0.75rem 0.85rem 0.9rem;
        border-top: 1px solid var(--line);
      }
      textarea {
        width: 100%;
        resize: vertical;
        min-height: 4.5rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        padding: 0.55rem 0.65rem;
        font: inherit;
        background: var(--bg);
      }
      .btn-primary,
      .btn-ghost {
        border-radius: var(--radius-sm);
        font: inherit;
        font-weight: 700;
        cursor: pointer;
        padding: 0.55rem 0.9rem;
      }
      .btn-primary {
        border: 0;
        background: var(--forest, #2f5d3a);
        color: #fff;
        justify-self: end;
      }
      .btn-primary:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-ghost {
        border: 1px solid var(--line);
        background: transparent;
        color: var(--forest-deep);
        font-size: 0.8rem;
        padding: 0.35rem 0.55rem;
      }
      .placeholder {
        display: grid;
        place-items: center;
        min-height: 16rem;
        color: var(--muted);
        padding: 1.5rem;
        text-align: center;
      }
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        border: 0;
      }
      @media (max-width: 779px) {
        .layout.has-thread .inbox {
          display: none;
        }
        .layout:not(.has-thread) .thread {
          display: none;
        }
      }
    `,
  ],
})
export class MessagingComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  protected readonly conversations = signal<ConversationRow[]>([]);
  protected readonly messages = signal<MessageRow[]>([]);
  protected readonly selected = signal<ConversationRow | null>(null);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly loadingList = signal(false);
  protected readonly loadingMessages = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected draft = '';

  protected myUserId(): number | null {
    return this.auth.userId();
  }

  ngOnInit(): void {
    this.loadConversations();
  }

  protected typeLabel(type: string): string {
    switch ((type || '').toUpperCase()) {
      case 'WALK':
        return 'Balade';
      case 'DIRECT':
        return 'Privé';
      case 'SUPPORT':
        return 'Support';
      case 'MODERATION':
        return 'Modération';
      default:
        return type;
    }
  }

  protected peers(conv: ConversationRow): MemberSummary[] {
    const me = this.myUserId();
    return (conv.members || []).filter((m) => m.userId !== me);
  }

  protected loadConversations(): void {
    this.loadingList.set(true);
    this.error.set(null);
    this.http.get<ConversationRow[]>('/api/messaging/conversations').subscribe({
      next: (rows) => {
        this.conversations.set(rows);
        this.loadingList.set(false);
        const sid = this.selectedId();
        if (sid != null) {
          const updated = rows.find((r) => r.id === sid) || null;
          this.selected.set(updated);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loadingList.set(false);
        this.error.set(this.apiError(err, 'Impossible de charger les conversations.'));
      },
    });
  }

  protected openConversation(c: ConversationRow): void {
    this.selected.set(c);
    this.selectedId.set(c.id);
    this.draft = '';
    this.loadMessages(c.id);
  }

  protected closeThread(): void {
    this.selected.set(null);
    this.selectedId.set(null);
    this.messages.set([]);
  }

  protected send(): void {
    const conv = this.selected();
    const body = this.draft.trim();
    if (!conv || !body || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.http.post<MessageRow>(`/api/messaging/conversations/${conv.id}/messages`, { body }).subscribe({
      next: (msg) => {
        this.sending.set(false);
        this.draft = '';
        this.messages.update((list) => [...list, msg]);
        this.loadConversations();
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        this.error.set(this.apiError(err, 'Envoi impossible.'));
      },
    });
  }

  protected startDirect(userId: number): void {
    this.error.set(null);
    this.info.set(null);
    this.http.post<ConversationRow>('/api/messaging/conversations/direct', { userId }).subscribe({
      next: (conv) => {
        this.info.set('Conversation privée ouverte.');
        this.loadConversations();
        this.openConversation(conv);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.apiError(err, 'Impossible d’ouvrir un message privé (adhésion / blocage).'));
      },
    });
  }

  protected blockUser(userId: number, displayName: string): void {
    if (!confirm(`Bloquer ${displayName} ? Cette personne ne pourra plus vous écrire.`)) {
      return;
    }
    this.http.post(`/api/messaging/blocks/${userId}`, {}, { responseType: 'text' }).subscribe({
      next: () => {
        this.info.set(`${displayName} a été bloqué(e).`);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.apiError(err, 'Blocage impossible.'));
      },
    });
  }

  private loadMessages(conversationId: number): void {
    this.loadingMessages.set(true);
    this.http.get<MessageRow[]>(`/api/messaging/conversations/${conversationId}/messages`).subscribe({
      next: (rows) => {
        this.messages.set(rows);
        this.loadingMessages.set(false);
        this.http.post<ConversationRow>(`/api/messaging/conversations/${conversationId}/read`, {}).subscribe({
          next: (updated) => {
            this.selected.set(updated);
            this.conversations.update((list) =>
              list.map((c) => (c.id === updated.id ? updated : c)),
            );
          },
          error: () => {
            /* lecture best-effort */
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.loadingMessages.set(false);
        this.error.set(this.apiError(err, 'Impossible de charger les messages.'));
      },
    });
  }

  private apiError(err: HttpErrorResponse, fallback: string): string {
    const msg = err?.error?.message;
    return typeof msg === 'string' && msg.trim() ? msg : fallback;
  }
}
