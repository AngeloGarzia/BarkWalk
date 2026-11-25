// Réservation d'une balade (/booking?walkId=…) et suivi de mes réservations (/booking).
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Booking, Walk } from '../../models/booking';
import { Dog } from '../../models/dog';
import { BookingService } from '../../services/booking.service';
import { DogService } from '../../services/dog.service';
import {
  bookingErrorMessage,
  bookingPrice,
  bookingStatusLabel,
  canCancel,
  dogEligibility,
  Eligibility,
  formatPrice,
  isWaitlistedResponse,
} from './booking-rules';

type Outcome = 'confirmed' | 'pending-payment' | 'waitlisted';

interface DogOption {
  dog: Dog;
  eligibility: Eligibility;
}

interface BookingRow {
  booking: Booking;
  walk: Walk | null;
  dogName: string;
  upcoming: boolean;
}

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="booking-page">
      @if (walkId()) {
        <a routerLink="/catalog" class="back">← Toutes les balades</a>

        @if (loading()) {
          <p class="muted">Chargement de la balade…</p>
        } @else if (!walk()) {
          <div class="banner error" role="alert">{{ error() || 'Balade introuvable.' }}</div>
        } @else {
          @let w = walk()!;
          <section class="bw-card walk-summary" aria-labelledby="walk-title">
            <div class="pills">
              <span [class]="typePillClass(w.walkType)">{{ typeLabel(w.walkType) }}</span>
              <span class="bw-pill" [class.bw-pill-urgent]="isFull()" [class.bw-pill-soft]="!isFull()">
                {{ seatsLabel() }}
              </span>
            </div>
            <h1 id="walk-title">{{ w.title }}</h1>
            <p class="when">{{ w.startsAt | date: "EEEE d MMMM 'à' HH'h'mm" }} · {{ formatDuration(w.durationMinutes) }}</p>
            <p class="where">
              {{ w.meetingPointName || 'Point de rendez-vous à préciser' }}
              @if (w.meetingPointAddress) {
                <span class="muted"> — {{ w.meetingPointAddress }}</span>
              }
            </p>
            @if (w.description) {
              <p class="description">{{ w.description }}</p>
            }
          </section>

          @if (outcome(); as result) {
            <section [class]="'bw-card outcome ' + result" role="status">
              @switch (result) {
                @case ('confirmed') {
                  <h2>C’est réservé 🎉</h2>
                  <p>{{ selectedDogName() }} est inscrit à « {{ w.title }} ». À bientôt au point de rendez-vous !</p>
                }
                @case ('pending-payment') {
                  <h2>Place bloquée 15 minutes</h2>
                  <p>
                    La place de {{ selectedDogName() }} est réservée en attente du paiement de
                    {{ formatPrice(price()) }}. Le paiement en ligne n’est pas encore disponible :
                    sans paiement d’ici 15 minutes, la place sera libérée.
                  </p>
                }
                @case ('waitlisted') {
                  <h2>Vous êtes sur liste d’attente</h2>
                  <p>La balade est complète. {{ selectedDogName() }} est inscrit sur la liste d’attente.</p>
                }
              }
              <div class="actions">
                <a routerLink="/booking" class="bw-btn bw-btn-forest">Mes réservations</a>
                <a routerLink="/catalog" class="bw-btn bw-btn-ghost">Autres balades</a>
              </div>
            </section>
          } @else if (!isBookable()) {
            <div class="banner error" role="alert">Cette balade n’est plus ouverte aux réservations.</div>
          } @else {
            <form class="bw-card booking-form" (ngSubmit)="submit()">
              <fieldset>
                <legend>Quel chien vient en balade ?</legend>
                @if (dogOptions().length === 0) {
                  <p class="muted">
                    Vous n’avez pas encore de chien enregistré.
                    <a routerLink="/dogs" class="link">Ajouter mon chien</a>
                  </p>
                }
                @for (option of dogOptions(); track option.dog.id) {
                  <label class="dog-option" [class.disabled]="!option.eligibility.eligible">
                    <input
                      type="radio"
                      name="dog"
                      [value]="option.dog.id"
                      [ngModel]="selectedDogId()"
                      (ngModelChange)="selectedDogId.set($event)"
                      [disabled]="!option.eligibility.eligible"
                    />
                    <span class="dog-text">
                      <strong>{{ option.dog.name }}</strong>
                      <span class="muted">{{ option.dog.breed }}</span>
                      @if (option.eligibility.reason) {
                        <span class="reason">{{ option.eligibility.reason }}</span>
                      }
                    </span>
                  </label>
                }
              </fieldset>

              @if (w.walkType === 'COMMUNITY') {
                @if (!isMember()) {
                  <div class="banner error">Les balades communautaires sont réservées aux adhérents.</div>
                }
                <label class="check">
                  <input type="checkbox" name="communityAck" [ngModel]="communityAck()" (ngModelChange)="communityAck.set($event)" />
                  <span>
                    J’ai compris que cette balade est organisée entre adhérents, sans encadrant professionnel,
                    et que je reste responsable de mon chien.
                  </span>
                </label>
              }

              @if (w.walkType === 'STAFF_GUIDED' && !isMember()) {
                <label class="check">
                  <input type="checkbox" name="trial" [ngModel]="trial()" (ngModelChange)="trial.set($event)" />
                  <span>Utiliser ma balade d’essai gratuite (une seule par compte)</span>
                </label>
              }

              <div class="price-line">
                <span>Prix</span>
                <strong>{{ formatPrice(price()) }}</strong>
              </div>
              @if (price() > 0) {
                <p class="muted small">
                  Le paiement en ligne arrive bientôt : votre place sera bloquée 15 minutes.
                </p>
              }
              @if (isFull()) {
                <p class="muted small">La balade est complète : votre chien sera inscrit sur la liste d’attente.</p>
              }

              @if (error()) {
                <div class="banner error" role="alert">{{ error() }}</div>
              }

              <button type="submit" class="bw-btn bw-btn-primary submit" [disabled]="!canSubmit()">
                {{ submitting() ? 'Réservation…' : isFull() ? 'Rejoindre la liste d’attente' : 'Réserver' }}
              </button>
            </form>
          }
        }
      } @else {
        <header class="page-header">
          <h1>Mes réservations</h1>
          <p class="subtitle">Suivez et gérez vos inscriptions aux balades.</p>
        </header>

        @if (notice()) {
          <div class="banner success" role="status">{{ notice() }}</div>
        }
        @if (error()) {
          <div class="banner error" role="alert">{{ error() }}</div>
        }

        @if (loading()) {
          <p class="muted">Chargement de vos réservations…</p>
        } @else if (rows().length === 0) {
          <div class="empty">
            <span class="icon" aria-hidden="true">🐾</span>
            <p>Aucune réservation pour l’instant.</p>
            <a routerLink="/catalog" class="bw-btn bw-btn-primary">Trouver une balade</a>
          </div>
        } @else {
          @if (upcomingRows().length > 0) {
            <h2 class="section-title">À venir</h2>
            @for (row of upcomingRows(); track row.booking.id) {
              <article class="bw-card booking-row">
                <div class="row-head">
                  <h3>{{ row.walk?.title || 'Balade #' + row.booking.walkId }}</h3>
                  <span class="bw-pill" [class]="statusPillClass(row.booking.status)">
                    {{ statusLabel(row.booking.status) }}
                  </span>
                </div>
                @if (row.walk) {
                  <p class="muted">{{ row.walk.startsAt | date: "EEE d MMM · HH'h'mm" }} · {{ row.walk.meetingPointName || row.walk.cityName }}</p>
                }
                <p class="row-meta">{{ row.dogName }} · {{ formatPrice(row.booking.priceCents) }}</p>
                @if (canCancel(row.booking.status)) {
                  <button
                    type="button"
                    class="bw-btn bw-btn-ghost cancel"
                    [disabled]="cancellingId() === row.booking.id"
                    (click)="cancel(row)"
                  >
                    {{ cancellingId() === row.booking.id ? 'Annulation…' : 'Annuler' }}
                  </button>
                }
              </article>
            }
          }
          @if (pastRows().length > 0) {
            <h2 class="section-title">Historique</h2>
            @for (row of pastRows(); track row.booking.id) {
              <article class="bw-card booking-row past">
                <div class="row-head">
                  <h3>{{ row.walk?.title || 'Balade #' + row.booking.walkId }}</h3>
                  <span class="bw-pill" [class]="statusPillClass(row.booking.status)">
                    {{ statusLabel(row.booking.status) }}
                  </span>
                </div>
                @if (row.walk) {
                  <p class="muted">{{ row.walk.startsAt | date: "EEE d MMM y · HH'h'mm" }}</p>
                }
                <p class="row-meta">{{ row.dogName }}</p>
              </article>
            }
          }
        }
      }
    </div>
  `,
  styles: [
    `
      .booking-page {
        max-width: 560px;
        margin: 0 auto;
        padding: 1.25rem 1rem calc(var(--nav-h) + 1.5rem);
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .back {
        color: var(--forest);
        font-weight: 600;
        font-size: 0.9rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0.4rem 0 0.25rem;
        font-size: 1.5rem;
        line-height: 1.2;
      }
      .subtitle {
        color: var(--muted);
        margin: 0;
      }
      .pills {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
      }
      .when {
        margin: 0.25rem 0;
        font-weight: 600;
      }
      .when::first-letter {
        text-transform: uppercase;
      }
      .where,
      .description {
        margin: 0.25rem 0 0;
      }
      .description {
        color: var(--muted);
      }
      .booking-form {
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
      }
      fieldset {
        border: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      legend {
        font-family: var(--font-display);
        font-weight: 700;
        font-size: 1.05rem;
        margin-bottom: 0.5rem;
      }
      .dog-option {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.75rem 0.85rem;
        border: 1.5px solid var(--line);
        border-radius: var(--radius-sm);
        cursor: pointer;
      }
      .dog-option:has(input:checked) {
        border-color: var(--forest);
        background: var(--surface-soft);
      }
      .dog-option.disabled {
        cursor: not-allowed;
        opacity: 0.6;
      }
      .dog-option input,
      .check input {
        margin-top: 0.2rem;
        accent-color: var(--forest);
        width: 1.1rem;
        height: 1.1rem;
        flex: none;
      }
      .dog-text {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
      }
      .reason {
        color: var(--warn);
        font-size: 0.85rem;
      }
      .check {
        display: flex;
        gap: 0.65rem;
        font-size: 0.92rem;
        cursor: pointer;
      }
      .price-line {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        padding-top: 0.75rem;
        border-top: 1px solid var(--line);
      }
      .price-line strong {
        font-family: var(--font-display);
        font-size: 1.2rem;
      }
      .small {
        font-size: 0.85rem;
        margin: 0;
      }
      .submit {
        width: 100%;
      }
      .submit:disabled {
        opacity: 0.5;
        cursor: not-allowed;
        box-shadow: none;
      }
      .outcome h2 {
        margin: 0 0 0.4rem;
        font-size: 1.2rem;
      }
      .outcome.confirmed {
        border-color: var(--forest);
        background: var(--surface-soft);
      }
      .outcome.pending-payment,
      .outcome.waitlisted {
        border-color: #f0c99a;
        background: #fdf6ec;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        margin-top: 0.75rem;
      }
      .link {
        color: var(--forest);
        font-weight: 600;
        text-decoration: underline;
      }
      .section-title {
        font-size: 1.05rem;
        margin: 0.5rem 0 0;
      }
      .booking-row {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .booking-row.past {
        opacity: 0.75;
      }
      .row-head {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 0.75rem;
      }
      .row-head h3 {
        margin: 0;
        font-size: 1rem;
      }
      .booking-row p {
        margin: 0;
      }
      .row-meta {
        font-size: 0.9rem;
      }
      .cancel {
        align-self: flex-start;
        margin-top: 0.5rem;
        min-height: 2.25rem;
        padding: 0.4rem 1rem;
        color: var(--danger);
        border-color: #f3c9c4;
      }
      .empty {
        text-align: center;
        padding: 3rem 1rem;
        color: var(--muted);
      }
      .icon {
        font-size: 2.5rem;
        display: block;
        margin-bottom: 0.75rem;
      }
    `,
  ],
})
export class BookingComponent {
  private readonly bookings = inject(BookingService);
  private readonly dogsApi = inject(DogService);
  private readonly auth = inject(AuthService);

  /** Lié au paramètre de requête ?walkId= (withComponentInputBinding). */
  readonly walkId = input<string | undefined>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  // Parcours de réservation
  protected readonly walk = signal<Walk | null>(null);
  protected readonly dogs = signal<Dog[]>([]);
  protected readonly myBookings = signal<Booking[]>([]);
  protected readonly selectedDogId = signal<number | null>(null);
  protected readonly communityAck = signal(false);
  protected readonly trial = signal(false);
  protected readonly submitting = signal(false);
  protected readonly outcome = signal<Outcome | null>(null);

  // Liste « Mes réservations »
  protected readonly rows = signal<BookingRow[]>([]);
  protected readonly cancellingId = signal<number | null>(null);

  protected readonly isMember = computed(() =>
    this.auth.roles().some((role) => role === 'MEMBER' || role === 'ADMIN'),
  );

  protected readonly dogOptions = computed<DogOption[]>(() => {
    const walk = this.walk();
    if (!walk) {
      return [];
    }
    const activeOnWalk = new Set(
      this.myBookings()
        .filter((b) => b.walkId === walk.id && !b.status.startsWith('CANCELLED'))
        .map((b) => b.dogId),
    );
    return this.dogs().map((dog) => ({ dog, eligibility: dogEligibility(dog, walk, activeOnWalk) }));
  });

  protected readonly price = computed(() => {
    const walk = this.walk();
    return walk ? bookingPrice(walk, this.isMember(), this.trial()) : 0;
  });

  protected readonly isFull = computed(() => {
    const walk = this.walk();
    return !!walk && walk.bookedCount >= walk.capacity;
  });

  protected readonly isBookable = computed(() => {
    const walk = this.walk();
    return !!walk && walk.status === 'PUBLISHED' && new Date(walk.startsAt).getTime() > Date.now();
  });

  protected readonly canSubmit = computed(() => {
    const walk = this.walk();
    if (!walk || this.submitting() || this.selectedDogId() === null) {
      return false;
    }
    if (walk.walkType === 'COMMUNITY' && (!this.communityAck() || !this.isMember())) {
      return false;
    }
    return true;
  });

  protected readonly selectedDogName = computed(
    () => this.dogs().find((d) => d.id === this.selectedDogId())?.name ?? 'Votre chien',
  );

  protected readonly upcomingRows = computed(() => this.rows().filter((r) => r.upcoming));
  protected readonly pastRows = computed(() => this.rows().filter((r) => !r.upcoming));

  protected readonly formatPrice = formatPrice;
  protected readonly canCancel = canCancel;
  protected readonly statusLabel = bookingStatusLabel;

  constructor() {
    effect(() => {
      const id = Number(this.walkId());
      this.error.set(null);
      this.notice.set(null);
      this.outcome.set(null);
      if (this.walkId() && Number.isInteger(id) && id > 0) {
        this.loadBookingFlow(id);
      } else if (this.walkId()) {
        this.loading.set(false);
        this.walk.set(null);
      } else {
        this.loadMyBookings();
      }
    });
  }

  protected submit(): void {
    const walk = this.walk();
    const dogId = this.selectedDogId();
    if (!walk || dogId === null || !this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.bookings
      .create({
        walkId: walk.id,
        dogId,
        communityAck: walk.walkType === 'COMMUNITY' && this.communityAck(),
        trial: this.trial(),
      })
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          this.outcome.set(booking.status === 'PENDING_PAYMENT' ? 'pending-payment' : 'confirmed');
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          const title = err.error?.title as string | undefined;
          if (isWaitlistedResponse(err.status, title)) {
            this.outcome.set('waitlisted');
          } else {
            this.error.set(bookingErrorMessage(err.status, title));
          }
        },
      });
  }

  protected cancel(row: BookingRow): void {
    const what = row.walk ? `« ${row.walk.title} »` : 'cette balade';
    if (!window.confirm(`Annuler la réservation de ${row.dogName} pour ${what} ?`)) {
      return;
    }
    this.cancellingId.set(row.booking.id);
    this.error.set(null);
    this.notice.set(null);
    this.bookings.cancel(row.booking.id).subscribe({
      next: (updated) => {
        this.cancellingId.set(null);
        this.rows.update((rows) =>
          rows.map((r) => (r.booking.id === updated.id ? { ...r, booking: updated, upcoming: false } : r)),
        );
        this.notice.set('Réservation annulée.');
      },
      error: (err: HttpErrorResponse) => {
        this.cancellingId.set(null);
        this.error.set(
          err.status === 409 ? 'Cette réservation ne peut plus être annulée.' : bookingErrorMessage(err.status),
        );
      },
    });
  }

  protected typeLabel(walkType: string): string {
    switch (walkType) {
      case 'COMMUNITY':
        return 'Communautaire · non encadrée';
      case 'INDEP_GUIDED':
        return 'Encadrée · indépendant';
      default:
        return 'Encadrée';
    }
  }

  protected typePillClass(walkType: string): string {
    return walkType === 'COMMUNITY' ? 'bw-pill bw-pill-community' : 'bw-pill bw-pill-guided';
  }

  protected seatsLabel(): string {
    const walk = this.walk();
    if (!walk) {
      return '';
    }
    const left = Math.max(0, walk.capacity - walk.bookedCount);
    if (left === 0) {
      return 'Complet';
    }
    const plural = left > 1 ? 's' : '';
    return `${left} place${plural} libre${plural} sur ${walk.capacity}`;
  }

  protected statusPillClass(status: string): string {
    if (status === 'CONFIRMED') {
      return 'bw-pill bw-pill-member';
    }
    if (status === 'PENDING_PAYMENT') {
      return 'bw-pill bw-pill-warn';
    }
    return 'bw-pill bw-pill-soft';
  }

  protected formatDuration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) {
      return `${m} min`;
    }
    return m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, '0')}`;
  }

  private loadBookingFlow(walkId: number): void {
    this.loading.set(true);
    this.walk.set(null);
    this.selectedDogId.set(null);
    forkJoin({
      walk: this.bookings.walk(walkId),
      dogs: this.dogsApi.list().pipe(catchError(() => of([] as Dog[]))),
      mine: this.bookings.mine().pipe(catchError(() => of([] as Booking[]))),
    }).subscribe({
      next: ({ walk, dogs, mine }) => {
        this.walk.set(walk);
        this.dogs.set(dogs);
        this.myBookings.set(mine);
        const firstEligible = this.dogOptions().find((o) => o.eligibility.eligible);
        this.selectedDogId.set(firstEligible?.dog.id ?? null);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 404 ? 'Balade introuvable.' : 'Impossible de charger la balade.');
        this.loading.set(false);
      },
    });
  }

  private loadMyBookings(): void {
    this.loading.set(true);
    forkJoin({
      mine: this.bookings.mine(),
      dogs: this.dogsApi.list().pipe(catchError(() => of([] as Dog[]))),
    }).subscribe({
      next: ({ mine, dogs }) => {
        const dogNames = new Map(dogs.map((d) => [d.id, d.name]));
        const walkIds = [...new Set(mine.map((b) => b.walkId))];
        const walks$ = walkIds.length
          ? forkJoin(walkIds.map((id) => this.bookings.walk(id).pipe(catchError(() => of(null)))))
          : of([] as (Walk | null)[]);
        walks$.pipe(map((walks) => new Map(walkIds.map((id, i) => [id, walks[i]])))).subscribe((walksById) => {
          const now = Date.now();
          const rows = mine.map((booking) => {
            const walk = walksById.get(booking.walkId) ?? null;
            const upcoming = canCancel(booking.status) && (!walk || new Date(walk.startsAt).getTime() > now);
            return { booking, walk, dogName: dogNames.get(booking.dogId) ?? 'Chien', upcoming };
          });
          rows.sort((a, b) => startTime(a) - startTime(b));
          this.rows.set(rows);
          this.loading.set(false);
        });
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(bookingErrorMessage(err.status));
        this.loading.set(false);
      },
    });
  }
}

function startTime(row: BookingRow): number {
  return row.walk ? new Date(row.walk.startsAt).getTime() : Number.MAX_SAFE_INTEGER;
}
