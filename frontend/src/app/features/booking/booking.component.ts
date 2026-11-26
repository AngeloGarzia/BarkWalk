// Réservation : création (R1/R2/R3/R9), liste, annulation (R4), waitlist (R7), paiement en attente (R6).
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Dog } from '../../models/dog';
import { WalkDifficultyBarComponent } from '../../shared/walk-difficulty-bar/walk-difficulty-bar.component';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../../shared/photo-uploader/photo-uploader.component';

interface WalkDetail {
  id: number;
  title: string;
  description?: string | null;
  walkType: string;
  cityName?: string | null;
  meetingPointName?: string | null;
  startsAt: string;
  durationMinutes: number;
  elevationGainM?: number;
  terrainType?: string;
  difficultyLevel?: number;
  organizerDisplayName?: string | null;
  promoPhotoUrls?: string[] | null;
  recycledFromWalkId?: number | null;
  galleryCount?: number;
  capacity: number;
  bookedCount: number;
  priceCents: number;
  nonMemberPriceCents?: number | null;
  status: string;
  communityDisclaimerRequired?: boolean;
}

interface BookingRow {
  id: number;
  walkId: number;
  dogId: number;
  status: string;
  priceCents: number;
  paymentRequired: boolean;
  confirmedAt?: string | null;
  paymentExpiresAt?: string | null;
  trial: boolean;
  communityAck: boolean;
  walkTitle?: string | null;
  walkType?: string | null;
  startsAt?: string | null;
  cityName?: string | null;
  dogName?: string | null;
  organizerDisplayName?: string | null;
  participantDisplayName?: string | null;
}

interface WaitlistRow {
  id: number;
  walkId: number;
  dogId: number;
  position: number;
  status: string;
  offeredAt?: string | null;
  offerExpiresAt?: string | null;
  walkTitle?: string | null;
  walkType?: string | null;
  startsAt?: string | null;
  dogName?: string | null;
}

interface RateableWalk {
  walkId: number;
  walkTitle?: string | null;
  startsAt?: string | null;
  canRate: boolean;
}

interface WalkRatingRow {
  id: number;
  walkId: number;
  walkTitle?: string | null;
  startsAt?: string | null;
  rating: number;
  comment?: string | null;
  createdAt: string;
}

interface WalkHistoryPayload {
  walkId: number;
  recycledFromWalkId?: number | null;
  lineageWalkIds: number[];
  photos: Array<{ id: number; url: string; kind: string; sortOrder: number }>;
  ratingComments: Array<{
    walkId: number;
    userId: number;
    displayName: string;
    rating: number;
    comment: string;
    createdAt: string;
  }>;
}

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    WalkDifficultyBarComponent,
    PhotoUploaderComponent,
  ],
  template: `
    <div class="booking-page">
      <header class="page-header">
        <h1>{{ walkId() ? 'Réserver une balade' : 'Mes réservations' }}</h1>
        <p class="subtitle">
          @if (walkId()) {
            Choisissez un chien éligible. Les règles club (dossier, compatibilité, adhésion) sont
            vérifiées côté serveur.
          } @else {
            Suivez vos réservations, paiements en attente et places en liste d’attente.
          }
        </p>
      </header>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }
      @if (info()) {
        <p class="banner info" role="status">{{ info() }}</p>
      }

      @if (walkId(); as wid) {
        <section class="panel">
          @if (walkLoading()) {
            <p class="muted">Chargement de la balade…</p>
          } @else if (selectedWalk(); as walk) {
            <div class="walk-summary">
              @if (walk.promoPhotoUrls?.[0]; as thumb) {
                <img class="promo-hero" [src]="thumb" [alt]="walk.title" />
              }
              <h2>{{ walk.title }}</h2>
              <p class="meta">
                {{ walk.cityName || 'Ville' }}
                @if (walk.meetingPointName) {
                  · {{ walk.meetingPointName }}
                }
                · {{ walk.startsAt | date: 'short' }}
              </p>
              <div class="tags">
                <span class="tag">{{ walkTypeLabel(walk.walkType) }}</span>
                @if (walk.organizerDisplayName) {
                  <span class="tag">{{ walk.organizerDisplayName }}</span>
                }
                <span class="tag">{{ walk.durationMinutes }} min</span>
                @if (walk.difficultyLevel) {
                  <span class="tag tag-diff">
                    <app-walk-difficulty-bar [level]="walk.difficultyLevel" />
                  </span>
                }
                <span class="tag">{{ seatsLeft(walk) }}</span>
              </div>
              @if (walk.description) {
                <p class="desc">{{ walk.description }}</p>
              }
            </div>

            @if (walk.recycledFromWalkId) {
              <section class="history-block">
                <h3>Édition précédente</h3>
                @if (historyLoading()) {
                  <p class="muted">Chargement de l’historique…</p>
                } @else if (history(); as h) {
                  @if (h.photos.length === 0 && h.ratingComments.length === 0) {
                    <p class="muted">Pas encore de photos ni d’avis sur les éditions passées.</p>
                  }
                  @if (h.photos.length) {
                    <ul class="history-photos">
                      @for (ph of h.photos; track ph.id) {
                        <li>
                          <img [src]="ph.url" [alt]="ph.kind" />
                        </li>
                      }
                    </ul>
                  }
                  @if (h.ratingComments.length) {
                    <ul class="history-comments">
                      @for (c of h.ratingComments; track c.userId + '-' + c.createdAt) {
                        <li>
                          <strong>{{ c.displayName }}</strong>
                          · {{ '★'.repeat(c.rating) }}
                          <p>{{ c.comment }}</p>
                        </li>
                      }
                    </ul>
                  }
                }
              </section>
            }

            @if (canUploadGallery(walk)) {
              <section class="gallery-block">
                <app-photo-uploader
                  title="Galerie de la balade"
                  [uploadUrl]="'/api/walks/' + walk.id + '/photos?kind=GALLERY'"
                  [deleteUrlBase]="'/api/walks/' + walk.id + '/photos'"
                  [max]="20"
                  [initialPhotos]="galleryPhotos()"
                  (photosChange)="galleryPhotos.set($event)"
                />
              </section>
            } @else if ((walk.galleryCount ?? 0) > 0 || galleryPhotos().length) {
              <section class="gallery-block">
                <h3>Galerie</h3>
                <ul class="history-photos">
                  @for (ph of galleryPhotos(); track ph.id) {
                    <li><img [src]="ph.url" alt="" /></li>
                  }
                </ul>
              </section>
            }

            @if (walk.status !== 'PUBLISHED') {
              <p class="banner warn">Cette balade n’est plus réservable ({{ walk.status }}).</p>
            } @else {
              <form class="form" [formGroup]="form" (ngSubmit)="submitBooking()">
                <label>
                  Chien
                  <select formControlName="dogId">
                    <option value="">Sélectionner…</option>
                    @for (dog of bookableDogs(); track dog.id) {
                      <option [value]="dog.id">{{ dog.name }} ({{ dog.dossierStatus }})</option>
                    }
                  </select>
                </label>
                @if (dogs().length === 0) {
                  <p class="field-hint">
                    Aucun chien.
                    <a routerLink="/dogs">Ajouter un chien</a>
                  </p>
                } @else if (bookableDogs().length === 0) {
                  <p class="field-hint">
                    Aucun chien avec dossier validé (R2 / R16).
                    <a routerLink="/dogs">Voir mes chiens</a>
                  </p>
                }

                @if (isCommunity(walk)) {
                  <label class="check">
                    <input formControlName="communityAck" type="checkbox" />
                    <span>
                      J’accepte le disclaimer communautaire (balade non encadrée professionnellement)
                      — obligatoire (R9).
                    </span>
                  </label>
                  @if (!isMember()) {
                    <p class="banner warn">
                      Adhésion requise pour les balades communautaires (R9).
                      <a routerLink="/profile">Voir mon profil</a>
                    </p>
                  }
                }

                @if (canOfferTrial(walk)) {
                  <label class="check">
                    <input formControlName="trial" type="checkbox" />
                    <span>Utiliser mon essai gratuit (1×, balade encadrée salarié uniquement — R1)</span>
                  </label>
                }

                <div class="price-box">
                  <span class="label">Tarif estimé</span>
                  <strong>
                    @if (estimatePriceCents(walk) === 0) {
                      Gratuit
                    } @else {
                      {{ estimatePriceCents(walk) / 100 | number: '1.0-2' }} €
                    }
                  </strong>
                  @if (estimatePriceCents(walk) > 0) {
                    <p class="field-hint">
                      Paiement requis sous délai club (R6). La place est libérée si non payée à temps.
                    </p>
                  }
                </div>

                <div class="form-actions">
                  <a routerLink="/booking" class="btn-ghost">Annuler</a>
                  <button
                    type="submit"
                    class="btn-primary"
                    [disabled]="form.invalid || saving() || walk.status !== 'PUBLISHED'"
                  >
                    {{ saving() ? 'Réservation…' : seatsLeftCount(walk) <= 0 ? 'Rejoindre la liste d’attente' : 'Confirmer' }}
                  </button>
                </div>
              </form>
            }
          } @else {
            <p class="muted">Balade introuvable.</p>
            <a routerLink="/catalog" class="btn-ghost">Catalogue</a>
          }
        </section>
      }

      <section class="panel">
        <div class="panel-head">
          <h2>Mes réservations</h2>
          <a routerLink="/catalog" class="btn-ghost linkish">Catalogue</a>
        </div>
        @if (bookingsLoading()) {
          <p class="muted">Chargement…</p>
        } @else if (bookings().length === 0) {
          <p class="muted empty">Aucune réservation pour le moment.</p>
        } @else {
          <ul class="list">
            @for (b of bookings(); track b.id) {
              <li class="card">
                <div class="card-main">
                  <h3>{{ b.walkTitle || 'Balade #' + b.walkId }}</h3>
                  <p class="meta">
                    @if (b.participantDisplayName) {
                      {{ b.participantDisplayName }} ·
                    }
                    {{ b.dogName || 'Chien #' + b.dogId }}
                    @if (b.organizerDisplayName) {
                      · avec {{ b.organizerDisplayName }}
                    }
                    @if (b.startsAt) {
                      · {{ b.startsAt | date: 'short' }}
                    }
                    @if (b.cityName) {
                      · {{ b.cityName }}
                    }
                  </p>
                  <div class="tags">
                    <span class="tag status" [attr.data-status]="b.status">{{ statusLabel(b.status) }}</span>
                    @if (b.trial) {
                      <span class="tag">Essai</span>
                    }
                    @if (b.paymentRequired && b.status === 'PENDING_PAYMENT') {
                      <span class="tag warn">
                        Paiement avant
                        {{ b.paymentExpiresAt ? (b.paymentExpiresAt | date: 'shortTime') : 'expiration' }}
                      </span>
                    }
                    <span class="tag">
                      {{ b.priceCents === 0 ? 'Gratuit' : (b.priceCents / 100 | number: '1.0-2') + ' €' }}
                    </span>
                  </div>
                  @if (b.status === 'PENDING_PAYMENT') {
                    <p class="field-hint">
                      Statut R6 : place réservée en attente de paiement. Sans paiement dans le délai, la
                      place est libérée automatiquement.
                    </p>
                  }
                  @if (isCancellable(b)) {
                    <p class="field-hint">
                      Annulation (R4) : gratuite si assez tôt avant le départ ; sinon tardive et
                      comptabilisée.
                    </p>
                  }
                </div>
                <div class="card-actions">
                  @if (isCancellable(b)) {
                    <button
                      type="button"
                      class="btn-danger"
                      [disabled]="actingId() === b.id"
                      (click)="cancelBooking(b)"
                    >
                      {{ actingId() === b.id ? 'Annulation…' : 'Annuler' }}
                    </button>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>Noter une balade</h2>
        </div>
        <p class="field-hint section-hint">
          Ouvert dès le lendemain de la balade, note de 1 à 5 ★ (fenêtre d’avis club).
        </p>
        @if (rateableLoading()) {
          <p class="muted">Chargement…</p>
        } @else if (rateable().length === 0) {
          <p class="muted empty">Aucune balade à noter pour le moment.</p>
        } @else {
          <ul class="list">
            @for (r of rateable(); track r.walkId) {
              <li class="card">
                <div class="card-main">
                  <h3>{{ r.walkTitle || 'Balade #' + r.walkId }}</h3>
                  <p class="meta">
                    @if (r.startsAt) {
                      {{ r.startsAt | date: 'short' }} ·
                    }
                    À noter
                  </p>
                  <div class="stars" role="group" [attr.aria-label]="'Note pour ' + (r.walkTitle || 'balade')">
                    @for (star of starValues; track star) {
                      <button
                        type="button"
                        class="star-btn"
                        [class.active]="(draftStars()[r.walkId] ?? 0) >= star"
                        [disabled]="actingId() === r.walkId"
                        (click)="setDraftStar(r.walkId, star)"
                      >
                        ★
                      </button>
                    }
                  </div>
                  <label class="rating-comment">
                    Commentaire (optionnel)
                    <input
                      type="text"
                      maxlength="400"
                      [value]="draftComments()[r.walkId] ?? ''"
                      (input)="setDraftComment(r.walkId, $event)"
                    />
                  </label>
                </div>
                <div class="card-actions">
                  <button
                    type="button"
                    class="btn-primary"
                    [disabled]="!(draftStars()[r.walkId] > 0) || actingId() === r.walkId"
                    (click)="submitRating(r)"
                  >
                    {{ actingId() === r.walkId ? 'Envoi…' : 'Envoyer' }}
                  </button>
                </div>
              </li>
            }
          </ul>
        }

        @if (myRatings().length > 0) {
          <div class="rated-block">
            <h3 class="subhead">Mes notes</h3>
            <ul class="list">
              @for (n of myRatings(); track n.id) {
                <li class="card compact">
                  <div class="card-main">
                    <h3>{{ n.walkTitle || 'Balade #' + n.walkId }}</h3>
                    <p class="meta">{{ starsText(n.rating) }} · {{ n.createdAt | date: 'short' }}</p>
                    @if (n.comment) {
                      <p class="field-hint">{{ n.comment }}</p>
                    }
                  </div>
                </li>
              }
            </ul>
          </div>
        }
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>Liste d’attente</h2>
        </div>
        @if (waitlistLoading()) {
          <p class="muted">Chargement…</p>
        } @else if (waitlist().length === 0) {
          <p class="muted empty">Aucune inscription en file d’attente.</p>
        } @else {
          <ul class="list">
            @for (w of waitlist(); track w.id) {
              <li class="card">
                <div class="card-main">
                  <h3>{{ w.walkTitle || 'Balade #' + w.walkId }}</h3>
                  <p class="meta">
                    {{ w.dogName || 'Chien #' + w.dogId }}
                    · position {{ w.position }}
                    @if (w.startsAt) {
                      · {{ w.startsAt | date: 'short' }}
                    }
                  </p>
                  <div class="tags">
                    <span class="tag status" [attr.data-status]="w.status">{{ waitlistStatusLabel(w.status) }}</span>
                  </div>
                  @if (w.status === 'OFFERED') {
                    <p class="field-hint">
                      Place proposée (R7)
                      @if (w.offerExpiresAt) {
                        — à accepter avant {{ w.offerExpiresAt | date: 'short' }}
                      }
                    </p>
                  }
                </div>
                <div class="card-actions">
                  @if (w.status === 'OFFERED') {
                    <button
                      type="button"
                      class="btn-primary"
                      [disabled]="actingId() === w.id"
                      (click)="acceptWaitlist(w)"
                    >
                      {{ actingId() === w.id ? 'Acceptation…' : 'Accepter la place' }}
                    </button>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .booking-page {
        max-width: 760px;
        margin: 0 auto;
        padding: 1.25rem 1rem 5rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .page-header h1 {
        margin: 0 0 0.25rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
        font-size: 1.55rem;
      }
      .subtitle {
        margin: 0;
        color: var(--muted);
        font-size: 0.92rem;
      }
      .banner {
        margin: 0;
        padding: 0.7rem 0.85rem;
        border-radius: var(--radius-sm);
        font-size: 0.88rem;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
      }
      .banner.info {
        background: #e8f2e8;
        color: var(--forest);
      }
      .banner.warn {
        background: #fff4df;
        color: #8a5a00;
      }
      .panel {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 1rem;
        box-shadow: var(--shadow-sm);
      }
      .panel-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 0.75rem;
      }
      .panel-head h2,
      .walk-summary h2 {
        margin: 0;
        font-size: 1.05rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .walk-summary {
        margin-bottom: 1rem;
      }
      .promo-hero {
        width: 100%;
        max-height: 11rem;
        object-fit: cover;
        border-radius: var(--radius-sm);
        margin: 0 0 0.75rem;
        background: var(--bg);
      }
      .history-block,
      .gallery-block {
        margin: 0 0 1rem;
        padding: 0.75rem 0 0;
        border-top: 1px solid var(--line);
      }
      .history-block h3,
      .gallery-block h3 {
        margin: 0 0 0.55rem;
        font-size: 0.95rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .history-photos {
        list-style: none;
        margin: 0 0 0.75rem;
        padding: 0;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(5rem, 1fr));
        gap: 0.4rem;
      }
      .history-photos img {
        width: 100%;
        aspect-ratio: 1;
        object-fit: cover;
        border-radius: var(--radius-sm);
      }
      .history-comments {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
      }
      .history-comments li {
        background: var(--bg);
        border-radius: var(--radius-sm);
        padding: 0.55rem 0.65rem;
        font-size: 0.86rem;
      }
      .history-comments p {
        margin: 0.25rem 0 0;
      }
      .meta {
        margin: 0.25rem 0 0.5rem;
        color: var(--muted);
        font-size: 0.86rem;
      }
      .desc {
        margin: 0.5rem 0 0;
        color: var(--ink);
        font-size: 0.9rem;
      }
      .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
      }
      .tag {
        background: var(--forest-soft);
        color: var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.15rem 0.55rem;
        font-size: 0.72rem;
        font-weight: 700;
      }
      .tag.warn {
        background: #fff4df;
        color: #8a5a00;
      }
      .tag.status[data-status='CONFIRMED'] {
        background: #dff3e4;
      }
      .tag.status[data-status='PENDING_PAYMENT'] {
        background: #fff4df;
        color: #8a5a00;
      }
      .tag.status[data-status='CANCELLED_FREE'],
      .tag.status[data-status='CANCELLED_LATE'] {
        background: #fde8e8;
        color: var(--danger);
      }
      .tag.status[data-status='COMPLETED'] {
        background: #e8eef2;
        color: var(--muted);
      }
      .tag.status[data-status='OFFERED'] {
        background: #dff3e4;
      }
      .tag.status[data-status='WAITING'] {
        background: #eef3ea;
      }
      .form {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      label {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      select,
      input[type='text'] {
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        padding: 0.55rem 0.65rem;
        background: var(--bg);
        font-weight: 500;
      }
      .check {
        flex-direction: row;
        align-items: flex-start;
        gap: 0.55rem;
        font-weight: 500;
      }
      .check input {
        margin-top: 0.15rem;
      }
      .field-hint {
        margin: 0;
        font-size: 0.8rem;
        color: var(--muted);
        font-weight: 500;
      }
      .field-hint a {
        color: var(--forest);
        font-weight: 700;
      }
      .price-box {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        padding: 0.75rem;
        border-radius: var(--radius-sm);
        background: var(--surface-soft);
      }
      .price-box .label {
        font-size: 0.75rem;
        color: var(--muted);
        font-weight: 600;
      }
      .price-box strong {
        font-size: 1.15rem;
        color: var(--forest-deep);
      }
      .form-actions,
      .card-actions {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        flex-wrap: wrap;
      }
      .btn-primary,
      .btn-ghost,
      .btn-danger {
        border-radius: var(--radius-pill);
        padding: 0.55rem 0.95rem;
        font-weight: 700;
        font-family: var(--font-display);
        font-size: 0.88rem;
        cursor: pointer;
        border: none;
        text-decoration: none;
        display: inline-flex;
        align-items: center;
      }
      .btn-primary {
        background: var(--forest);
        color: var(--on-forest);
      }
      .btn-primary:disabled,
      .btn-danger:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-ghost {
        background: transparent;
        color: var(--muted);
        border: 1px solid var(--line);
      }
      .btn-danger {
        background: transparent;
        color: var(--danger);
        border: 1px solid var(--danger);
      }
      .linkish {
        font-size: 0.82rem;
      }
      .list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }
      .card {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.75rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
      }
      .card-main {
        min-width: 0;
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .card h3 {
        margin: 0;
        font-size: 0.98rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .muted,
      .empty {
        color: var(--muted);
        margin: 0;
      }
      .section-hint {
        margin: 0 0 0.75rem;
      }
      .stars {
        display: flex;
        gap: 0.2rem;
        margin: 0.25rem 0 0.5rem;
      }
      .star-btn {
        border: none;
        background: transparent;
        color: #cfc7b8;
        font-size: 1.45rem;
        line-height: 1;
        cursor: pointer;
        padding: 0.1rem;
      }
      .star-btn.active {
        color: var(--cta);
      }
      .star-btn:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .rating-comment {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      .rating-comment input {
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        padding: 0.45rem 0.55rem;
        background: var(--bg);
        font-weight: 500;
      }
      .rated-block {
        margin-top: 1rem;
        padding-top: 0.85rem;
        border-top: 1px solid var(--line);
      }
      .subhead {
        margin: 0 0 0.55rem;
        font-size: 0.95rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .card.compact {
        padding: 0.55rem 0.7rem;
      }
    `,
  ],
})
export class BookingComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly walkId = signal<number | null>(null);
  protected readonly selectedWalk = signal<WalkDetail | null>(null);
  protected readonly galleryPhotos = signal<PhotoItem[]>([]);
  protected readonly history = signal<WalkHistoryPayload | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly dogs = signal<Dog[]>([]);
  protected readonly bookings = signal<BookingRow[]>([]);
  protected readonly waitlist = signal<WaitlistRow[]>([]);
  protected readonly rateable = signal<RateableWalk[]>([]);
  protected readonly myRatings = signal<WalkRatingRow[]>([]);
  protected readonly draftStars = signal<Record<number, number>>({});
  protected readonly draftComments = signal<Record<number, string>>({});
  protected readonly starValues = [1, 2, 3, 4, 5];

  protected readonly walkLoading = signal(false);
  protected readonly bookingsLoading = signal(false);
  protected readonly waitlistLoading = signal(false);
  protected readonly rateableLoading = signal(false);
  protected readonly saving = signal(false);
  protected readonly actingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    dogId: ['', Validators.required],
    communityAck: [false],
    trial: [false],
  });

  protected readonly bookableDogs = computed(() =>
    this.dogs().filter((d) => d.dossierStatus === 'VALIDATED'),
  );

  ngOnInit(): void {
    const raw = this.route.snapshot.queryParamMap.get('walkId');
    const id = raw ? Number(raw) : NaN;
    this.walkId.set(Number.isFinite(id) ? id : null);

    this.loadDogs();
    this.loadBookings();
    this.loadWaitlist();
    this.loadRatings();

    if (this.walkId() != null) {
      this.loadWalk(this.walkId()!);
    }
  }

  protected setDraftStar(walkId: number, star: number): void {
    this.draftStars.update((m) => ({ ...m, [walkId]: star }));
  }

  protected setDraftComment(walkId: number, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.draftComments.update((m) => ({ ...m, [walkId]: value }));
  }

  protected starsText(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  protected submitRating(row: RateableWalk): void {
    const rating = this.draftStars()[row.walkId] ?? 0;
    if (rating < 1 || rating > 5 || this.actingId() != null) {
      return;
    }
    this.actingId.set(row.walkId);
    this.error.set(null);
    this.http
      .post('/api/walk-ratings', {
        walkId: row.walkId,
        rating,
        comment: (this.draftComments()[row.walkId] ?? '').trim() || null,
      })
      .subscribe({
        next: () => {
          this.actingId.set(null);
          this.info.set('Merci ! Votre note a été enregistrée.');
          this.loadRatings();
        },
        error: (err: HttpErrorResponse) => {
          this.actingId.set(null);
          this.error.set(this.apiError(err, 'Impossible d’enregistrer la note.'));
        },
      });
  }

  protected isMember(): boolean {
    return this.auth.hasRole('MEMBER') || this.auth.isAdmin();
  }

  protected isCommunity(walk: WalkDetail): boolean {
    return walk.walkType === 'COMMUNITY' || !!walk.communityDisclaimerRequired;
  }

  protected canOfferTrial(walk: WalkDetail): boolean {
    if (walk.walkType !== 'STAFF_GUIDED') {
      return false;
    }
    if (this.isMember()) {
      return false;
    }
    return !this.bookings().some((b) => b.trial);
  }

  protected estimatePriceCents(walk: WalkDetail): number {
    if (this.form.controls.trial.value && walk.walkType === 'STAFF_GUIDED') {
      return 0;
    }
    if (walk.walkType === 'COMMUNITY') {
      return 0;
    }
    if (walk.walkType === 'INDEP_GUIDED') {
      return walk.priceCents;
    }
    if (walk.walkType === 'STAFF_GUIDED') {
      if (this.isMember()) {
        return 0;
      }
      return walk.nonMemberPriceCents ?? walk.priceCents;
    }
    return walk.priceCents;
  }

  protected seatsLeft(walk: WalkDetail): string {
    const left = this.seatsLeftCount(walk);
    if (left <= 0) {
      return 'Complet — liste d’attente';
    }
    return `${left} place${left > 1 ? 's' : ''}`;
  }

  protected seatsLeftCount(walk: WalkDetail): number {
    return Math.max(0, walk.capacity - walk.bookedCount);
  }

  protected walkTypeLabel(type: string): string {
    switch (type) {
      case 'COMMUNITY':
        return 'Communautaire';
      case 'STAFF_GUIDED':
        return 'Encadrée salarié';
      case 'INDEP_GUIDED':
        return 'Encadrée indépendant';
      default:
        return type;
    }
  }

  protected statusLabel(status: string): string {
    switch (status) {
      case 'PENDING_PAYMENT':
        return 'Paiement en attente';
      case 'CONFIRMED':
        return 'Confirmée';
      case 'CANCELLED_FREE':
        return 'Annulée (gratuite)';
      case 'CANCELLED_LATE':
        return 'Annulée (tardive)';
      case 'COMPLETED':
        return 'Terminée';
      case 'NO_SHOW':
        return 'Absent';
      case 'WAITLISTED':
        return 'Liste d’attente';
      default:
        return status;
    }
  }

  protected waitlistStatusLabel(status: string): string {
    switch (status) {
      case 'WAITING':
        return 'En attente';
      case 'OFFERED':
        return 'Place proposée';
      case 'ACCEPTED':
        return 'Acceptée';
      case 'EXPIRED':
        return 'Offre expirée';
      case 'CANCELLED':
        return 'Annulée';
      default:
        return status;
    }
  }

  protected isCancellable(b: BookingRow): boolean {
    return b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT';
  }

  protected submitBooking(): void {
    const walk = this.selectedWalk();
    if (!walk || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isCommunity(walk) && !this.form.controls.communityAck.value) {
      this.error.set('Le disclaimer communautaire est obligatoire (R9).');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.info.set(null);
    const dogId = Number(this.form.controls.dogId.value);
    this.http
      .post<BookingRow>('/api/bookings', {
        walkId: walk.id,
        dogId,
        communityAck: this.form.controls.communityAck.value,
        trial: this.form.controls.trial.value,
      })
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          if (created.status === 'PENDING_PAYMENT') {
            this.info.set(
              'Réservation créée — paiement requis avant expiration (R6). La place sera libérée sinon.',
            );
          } else {
            this.info.set('Réservation confirmée.');
          }
          this.loadBookings();
          this.loadWalk(walk.id);
          this.router.navigate(['/booking']);
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          if (err.status === 409 && this.isWaitlistMessage(err)) {
            this.info.set('Balade complète — vous avez été ajouté à la liste d’attente (R7).');
            this.loadWaitlist();
            this.router.navigate(['/booking']);
            return;
          }
          this.error.set(this.apiError(err, 'Réservation impossible.'));
        },
      });
  }

  protected cancelBooking(b: BookingRow): void {
    if (this.actingId() != null) {
      return;
    }
    const lateHint =
      'Confirmer l’annulation ? Si trop proche du départ, elle sera tardive et comptabilisée (R4).';
    if (!window.confirm(lateHint)) {
      return;
    }
    this.actingId.set(b.id);
    this.error.set(null);
    this.http.post<BookingRow>(`/api/bookings/${b.id}/cancel`, {}).subscribe({
      next: (updated) => {
        this.actingId.set(null);
        if (updated.status === 'CANCELLED_LATE') {
          this.info.set('Annulation tardive enregistrée (comptabilisée — R4).');
        } else {
          this.info.set('Annulation gratuite enregistrée (R4).');
        }
        this.loadBookings();
        this.loadWaitlist();
      },
      error: (err: HttpErrorResponse) => {
        this.actingId.set(null);
        this.error.set(this.apiError(err, 'Annulation impossible.'));
      },
    });
  }

  protected acceptWaitlist(w: WaitlistRow): void {
    if (this.actingId() != null) {
      return;
    }
    this.actingId.set(w.id);
    this.error.set(null);
    this.http.post<BookingRow>(`/api/waitlist/${w.id}/accept`, {}).subscribe({
      next: () => {
        this.actingId.set(null);
        this.info.set('Place acceptée — réservation confirmée (R7).');
        this.loadBookings();
        this.loadWaitlist();
      },
      error: (err: HttpErrorResponse) => {
        this.actingId.set(null);
        this.error.set(this.apiError(err, 'Acceptation impossible.'));
        this.loadWaitlist();
      },
    });
  }

  protected canUploadGallery(walk: WalkDetail): boolean {
    return walk.status === 'IN_PROGRESS' || walk.status === 'COMPLETED';
  }

  private loadWalk(id: number): void {
    this.walkLoading.set(true);
    this.galleryPhotos.set([]);
    this.history.set(null);
    this.http.get<WalkDetail>(`/api/walks/${id}`).subscribe({
      next: (walk) => {
        this.selectedWalk.set(walk);
        this.walkLoading.set(false);
        this.loadGallery(id);
        if (walk.recycledFromWalkId) {
          this.loadHistory(id);
        }
        if (this.isCommunity(walk)) {
          this.form.controls.communityAck.setValidators([Validators.requiredTrue]);
        } else {
          this.form.controls.communityAck.clearValidators();
          this.form.controls.communityAck.setValue(false);
        }
        this.form.controls.communityAck.updateValueAndValidity();
      },
      error: () => {
        this.selectedWalk.set(null);
        this.walkLoading.set(false);
        this.error.set('Impossible de charger la balade.');
      },
    });
  }

  private loadGallery(walkId: number): void {
    this.http
      .get<Array<{ id: number; url: string; sortOrder: number }>>(`/api/walks/${walkId}/photos`, {
        params: { kind: 'GALLERY' },
      })
      .subscribe({
        next: (photos) =>
          this.galleryPhotos.set(
            photos.map((p) => ({ id: p.id, url: p.url, sortOrder: p.sortOrder })),
          ),
        error: () => this.galleryPhotos.set([]),
      });
  }

  private loadHistory(walkId: number): void {
    this.historyLoading.set(true);
    this.http.get<WalkHistoryPayload>(`/api/walks/${walkId}/history`).subscribe({
      next: (h) => {
        this.history.set(h);
        this.historyLoading.set(false);
      },
      error: () => {
        this.history.set(null);
        this.historyLoading.set(false);
      },
    });
  }

  private loadDogs(): void {
    this.http.get<Dog[]>('/api/dogs').subscribe({
      next: (dogs) => this.dogs.set(dogs),
      error: () => this.dogs.set([]),
    });
  }

  private loadBookings(): void {
    this.bookingsLoading.set(true);
    this.http.get<BookingRow[]>('/api/bookings').subscribe({
      next: (rows) => {
        this.bookings.set(rows);
        this.bookingsLoading.set(false);
      },
      error: () => {
        this.bookings.set([]);
        this.bookingsLoading.set(false);
        this.error.set('Impossible de charger vos réservations.');
      },
    });
  }

  private loadWaitlist(): void {
    this.waitlistLoading.set(true);
    this.http.get<WaitlistRow[]>('/api/waitlist/mine').subscribe({
      next: (rows) => {
        this.waitlist.set(rows);
        this.waitlistLoading.set(false);
      },
      error: () => {
        this.waitlist.set([]);
        this.waitlistLoading.set(false);
      },
    });
  }

  private loadRatings(): void {
    this.rateableLoading.set(true);
    this.http.get<RateableWalk[]>('/api/walk-ratings/rateable').subscribe({
      next: (rows) => {
        this.rateable.set(rows);
        this.rateableLoading.set(false);
      },
      error: () => {
        this.rateable.set([]);
        this.rateableLoading.set(false);
      },
    });
    this.http.get<WalkRatingRow[]>('/api/walk-ratings/mine').subscribe({
      next: (rows) => this.myRatings.set(rows),
      error: () => this.myRatings.set([]),
    });
  }

  private isWaitlistMessage(err: HttpErrorResponse): boolean {
    const msg = this.extractDetail(err)?.toLowerCase() ?? '';
    return msg.includes('waitlist') || msg.includes('full');
  }

  private apiError(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 401) {
      this.router.navigate(['/login']);
      return 'Connectez-vous pour réserver.';
    }
    if (err.status === 403) {
      return this.extractDetail(err) || 'Droits insuffisants (adhésion ou propriété du chien).';
    }
    return this.extractDetail(err) || fallback;
  }

  private extractDetail(err: HttpErrorResponse): string | null {
    const body = err.error;
    if (body && typeof body === 'object') {
      const detail =
        (body as { detail?: string; title?: string; message?: string; reason?: string }).detail
        ?? (body as { title?: string }).title
        ?? (body as { message?: string }).message
        ?? (body as { reason?: string }).reason;
      if (detail) {
        return String(detail);
      }
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    return null;
  }
}
