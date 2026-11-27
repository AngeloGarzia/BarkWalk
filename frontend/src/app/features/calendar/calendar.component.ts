// Agenda admin / organisateur — grille horaire mobile-first (jour → semaine).
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  addDays,
  addWeeks,
  format,
  isSameDay,
  isToday,
  startOfDay,
  startOfWeek,
} from 'date-fns';
import { fr } from 'date-fns/locale';
import { fromEvent } from 'rxjs';
import { WalkDifficultyBarComponent } from '../../shared/walk-difficulty-bar/walk-difficulty-bar.component';
import { WalkRouteBadgeComponent } from '../../shared/walk-route-badge/walk-route-badge.component';
import {
  bookingStatusLabel,
  energyLabel,
  sizeLabel,
  walkStatusLabel,
  walkTypeLabel,
} from '../../shared/labels';

interface CalWalk {
  id: number;
  title: string;
  cityName?: string | null;
  walkType: string;
  startsAt: string;
  durationMinutes: number;
  capacity: number;
  bookedCount: number;
  status: string;
  archivedAt?: string | null;
}

interface WalkDetail {
  id: number;
  title: string;
  description?: string | null;
  walkType: string;
  cityName?: string | null;
  department?: string | null;
  region?: string | null;
  meetingPointName?: string | null;
  meetingPointAddress?: string | null;
  startsAt: string;
  durationMinutes: number;
  elevationGainM?: number;
  terrainType?: string | null;
  difficultyLevel?: number;
  capacity: number;
  bookedCount: number;
  priceCents: number;
  nonMemberPriceCents?: number | null;
  organizerDisplayName?: string | null;
  status: string;
  startedAt?: string | null;
  completedAt?: string | null;
  archivedAt?: string | null;
  attendanceValidatedAt?: string | null;
  promoPhotoUrls?: string[] | null;
  galleryCount?: number;
  acceptedSizes?: string[] | null;
  acceptedEnergy?: string[] | null;
  minSociabilityDogs?: string | null;
  allowsDangerCategories?: boolean;
  routeHint?: string | null;
  routeGeojson?: { type: string; coordinates: [number, number][] } | string | null;
  communityDisclaimerRequired?: boolean;
  participantDisplayNames?: string[] | null;
}

interface WalkParticipant {
  bookingId: number;
  userId: number;
  dogId: number;
  displayName: string;
  dogName: string;
  bookingStatus: string;
  phone?: string | null;
}

interface DayColumn {
  date: Date;
  key: string;
  dayNum: string;
  weekday: string;
  isToday: boolean;
}

interface PositionedEvent {
  walk: CalWalk;
  topPct: number;
  heightPct: number;
  startMs: number;
  endMs: number;
  colorKey: string;
  label: string;
  timeLabel: string;
}

/** Groupe de balades qui se chevauchent : une pastille principale + les autres en pile. */
interface EventCluster {
  id: string;
  primary: PositionedEvent;
  items: PositionedEvent[];
  topPct: number;
  heightPct: number;
  extraCount: number;
}

const DAY_START_HOUR = 6;
const DAY_END_HOUR = 22;
const HOUR_COUNT = DAY_END_HOUR - DAY_START_HOUR;

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [DatePipe, DecimalPipe, WalkDifficultyBarComponent, WalkRouteBadgeComponent],
  template: `
    <div class="cal-page">
      <header class="cal-toolbar">
        <div class="section-label">Balades · Agenda</div>
        <div class="toolbar-row">
          <button type="button" class="btn-today" (click)="goToday()">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="4" y="5" width="16" height="15" rx="2" fill="none" stroke="currentColor" stroke-width="1.8" />
              <path d="M8 3v4M16 3v4M4 10h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            Aujourd’hui
          </button>
          <div class="nav-arrows">
            <button type="button" class="icon-btn" (click)="shift(-1)" aria-label="Période précédente">
              ‹
            </button>
            <button type="button" class="icon-btn" (click)="shift(1)" aria-label="Période suivante">
              ›
            </button>
          </div>
          <h1 class="range-title">{{ rangeLabel() }}</h1>
        </div>
        <div class="toolbar-row secondary">
          <div class="view-toggle" role="group" aria-label="Mode d’affichage">
            <button
              type="button"
              [class.active]="viewMode() === 'day'"
              (click)="setView('day')"
            >
              Jour
            </button>
            <button
              type="button"
              [class.active]="viewMode() === 'week'"
              (click)="setView('week')"
            >
              Semaine
            </button>
          </div>
          <p class="hint">{{ hintText() }}</p>
        </div>
      </header>

      <div class="legend" aria-label="Légende des statuts">
        @for (item of legend; track item.key) {
          <span class="legend-item" [attr.data-status]="item.key">{{ item.label }}</span>
        }
      </div>

      @if (loading()) {
        <p class="state">Chargement de l’agenda…</p>
      } @else if (error()) {
        <p class="state error">{{ error() }}</p>
      } @else {
        <div class="cal-board" [attr.data-cols]="days().length">
          <div class="cal-head">
            <div class="gutter-spacer" aria-hidden="true"></div>
            <div class="day-heads">
              @for (day of days(); track day.key) {
                <button
                  type="button"
                  class="day-head"
                  [class.today]="day.isToday"
                  [class.selected]="isSelectedDay(day.date)"
                  (click)="focusDay(day.date)"
                >
                  <span class="day-num">{{ day.dayNum }}</span>
                  <span class="day-name">{{ day.weekday }}</span>
                </button>
              }
            </div>
          </div>

          <div class="cal-body" #scrollBody>
            <div class="hours-gutter" aria-hidden="true">
              @for (h of hours; track h) {
                <div class="hour-label" [style.height.%]="100 / hourCount">{{ h }}</div>
              }
            </div>

            <div class="day-columns">
              @if (showNowLine()) {
                <div class="now-line" [style.top.%]="nowTopPct()" aria-hidden="true">
                  <span class="now-dot"></span>
                </div>
              }

              @for (day of days(); track day.key) {
                <div class="day-col" [class.today]="day.isToday">
                  <div class="hour-grid" aria-hidden="true">
                    @for (h of hours; track h) {
                      <div class="hour-slot">
                        <div class="half"></div>
                      </div>
                    }
                  </div>
                  @for (cluster of clustersForDay(day.date); track cluster.id) {
                    <div
                      class="event-stack"
                      [class.has-stack]="cluster.extraCount > 0"
                      [style.top.%]="cluster.topPct"
                      [style.height.%]="cluster.heightPct"
                    >
                      @if (cluster.extraCount > 0) {
                        <span class="stack-back back-2" aria-hidden="true"></span>
                        <span class="stack-back back-1" aria-hidden="true"></span>
                      }
                      <button
                        type="button"
                        class="event"
                        [class.compact]="cluster.extraCount > 0"
                        [attr.data-status]="cluster.primary.colorKey"
                        [attr.title]="cluster.primary.walk.title + ' · ' + cluster.primary.label"
                        (click)="openWalkDetail(cluster.primary.walk.id)"
                      >
                        <span class="ev-time">{{ cluster.primary.timeLabel }}</span>
                        <span class="ev-title">{{ cluster.primary.walk.title }}</span>
                        @if (cluster.extraCount === 0) {
                          <span class="ev-meta">
                            {{ cluster.primary.label }}
                            · {{ cluster.primary.walk.bookedCount }}/{{ cluster.primary.walk.capacity }}
                          </span>
                        }
                      </button>
                      @if (cluster.extraCount > 0) {
                        <button
                          type="button"
                          class="stack-badge"
                          [attr.aria-label]="cluster.extraCount + ' autres balades sur ce créneau'"
                          (click)="openClusterSheet($event, cluster)"
                        >
                          +{{ cluster.extraCount }}
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          </div>
        </div>

        @if (mobileDayList().length) {
          <section class="mobile-list" aria-label="Balades du jour">
            <h2>Ce jour</h2>
            <ul>
              @for (ev of mobileDayList(); track ev.walk.id) {
                <li [attr.data-status]="ev.colorKey">
                  <button type="button" (click)="openWalkDetail(ev.walk.id)">
                    <strong>{{ ev.timeLabel }}</strong>
                    <span>{{ ev.walk.title }}</span>
                    <em>{{ ev.label }} · {{ ev.walk.cityName || walkTypeLabel(ev.walk.walkType) }}</em>
                  </button>
                </li>
              }
            </ul>
          </section>
        }
      }

      @if (clusterSheet(); as sheet) {
        <div class="sheet-backdrop" (click)="closeClusterSheet()"></div>
        <div class="cluster-sheet" role="dialog" aria-modal="true" aria-label="Balades du créneau">
          <header class="sheet-head">
            <div>
              <h2>{{ sheet.items.length }} balades</h2>
              <p>{{ sheet.primary.timeLabel }} · créneau partagé</p>
            </div>
            <button type="button" class="sheet-close" (click)="closeClusterSheet()" aria-label="Fermer">
              ×
            </button>
          </header>
          <ul class="sheet-list">
            @for (ev of sheet.items; track ev.walk.id) {
              <li [attr.data-status]="ev.colorKey">
                <button type="button" (click)="openWalkDetail(ev.walk.id)">
                  <strong>{{ ev.timeLabel }}</strong>
                  <span>{{ ev.walk.title }}</span>
                  <em>
                    {{ ev.label }} · {{ ev.walk.bookedCount }}/{{ ev.walk.capacity }}
                    @if (ev.walk.cityName) {
                      · {{ ev.walk.cityName }}
                    }
                  </em>
                </button>
              </li>
            }
          </ul>
        </div>
      }

      @if (detailOpen()) {
        <div class="detail-backdrop" (click)="closeWalkDetail()" role="presentation"></div>
        <div
          class="detail-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cal-detail-title"
          (click)="$event.stopPropagation()"
        >
          <header class="detail-head">
            <div>
              <p class="detail-kicker">Détail balade</p>
              <h2 id="cal-detail-title">
                @if (detailWalk(); as walk) {
                  {{ walk.title }}
                } @else {
                  Chargement…
                }
              </h2>
            </div>
            <button type="button" class="sheet-close" (click)="closeWalkDetail()" aria-label="Fermer">
              ×
            </button>
          </header>

          @if (detailLoading()) {
            <p class="detail-state">Chargement des informations…</p>
          } @else if (detailError()) {
            <p class="detail-state error" role="alert">{{ detailError() }}</p>
          } @else if (detailWalk(); as walk) {
            <div class="detail-body">
              @if (walk.promoPhotoUrls?.[0]; as thumb) {
                <img class="detail-hero" [src]="thumb" [alt]="walk.title" />
              }

              <div class="detail-title-row">
                <app-walk-route-badge [routeGeojson]="walk.routeGeojson" />
                <span class="detail-status" [attr.data-status]="detailColorKey(walk)">
                  {{ detailStatusLabel(walk) }}
                </span>
              </div>

              <dl class="detail-grid">
                <div>
                  <dt>Type</dt>
                  <dd>{{ walkTypeLabel(walk.walkType) }}</dd>
                </div>
                <div>
                  <dt>Date</dt>
                  <dd>{{ formatWalkWhen(walk.startsAt) }}</dd>
                </div>
                <div>
                  <dt>Durée</dt>
                  <dd>{{ walk.durationMinutes }} min</dd>
                </div>
                <div>
                  <dt>Places</dt>
                  <dd>{{ walk.bookedCount }}/{{ walk.capacity }}</dd>
                </div>
                <div>
                  <dt>Ville</dt>
                  <dd>
                    {{ walk.cityName || '—' }}
                    @if (walk.department) {
                      · {{ walk.department }}
                    }
                    @if (walk.region) {
                      · {{ walk.region }}
                    }
                  </dd>
                </div>
                <div>
                  <dt>Point de rendez-vous</dt>
                  <dd>
                    {{ walk.meetingPointName || '—' }}
                    @if (walk.meetingPointAddress) {
                      <span class="detail-sub">{{ walk.meetingPointAddress }}</span>
                    }
                  </dd>
                </div>
                <div>
                  <dt>Organisateur</dt>
                  <dd>{{ walk.organizerDisplayName || '—' }}</dd>
                </div>
                <div>
                  <dt>Tarif adhérent</dt>
                  <dd>
                    @if (walk.priceCents <= 0) {
                      Gratuit
                    } @else {
                      {{ walk.priceCents / 100 | number: '1.0-2' }} €
                    }
                  </dd>
                </div>
                @if (walk.nonMemberPriceCents != null) {
                  <div>
                    <dt>Tarif non adhérent</dt>
                    <dd>
                      @if (walk.nonMemberPriceCents <= 0) {
                        Gratuit
                      } @else {
                        {{ walk.nonMemberPriceCents / 100 | number: '1.0-2' }} €
                      }
                    </dd>
                  </div>
                }
                @if (walk.difficultyLevel) {
                  <div>
                    <dt>Difficulté</dt>
                    <dd class="detail-diff">
                      <app-walk-difficulty-bar [level]="walk.difficultyLevel" />
                    </dd>
                  </div>
                }
                @if (walk.elevationGainM != null && walk.elevationGainM > 0) {
                  <div>
                    <dt>Dénivelé</dt>
                    <dd>{{ walk.elevationGainM }} m</dd>
                  </div>
                }
                @if (walk.terrainType) {
                  <div>
                    <dt>Terrain</dt>
                    <dd>{{ walk.terrainType }}</dd>
                  </div>
                }
                @if (walk.galleryCount) {
                  <div>
                    <dt>Galerie</dt>
                    <dd>{{ walk.galleryCount }} photo(s)</dd>
                  </div>
                }
              </dl>

              @if (walk.description) {
                <section class="detail-section">
                  <h3>Description</h3>
                  <p>{{ walk.description }}</p>
                </section>
              }

              @if (walk.routeHint) {
                <section class="detail-section">
                  <h3>Itinéraire</h3>
                  <p>{{ walk.routeHint }}</p>
                </section>
              }

              @if ((walk.acceptedSizes?.length || 0) > 0 || (walk.acceptedEnergy?.length || 0) > 0) {
                <section class="detail-section">
                  <h3>Critères chiens</h3>
                  <ul class="detail-chips">
                    @for (s of walk.acceptedSizes || []; track s) {
                      <li>{{ sizeLabel(s) }}</li>
                    }
                    @for (e of walk.acceptedEnergy || []; track e) {
                      <li>{{ energyLabel(e) }}</li>
                    }
                    @if (walk.minSociabilityDogs) {
                      <li>Sociabilité min. {{ walk.minSociabilityDogs }}</li>
                    }
                    @if (walk.allowsDangerCategories) {
                      <li>Catégories dangereuses acceptées</li>
                    }
                  </ul>
                </section>
              }

              @if (walk.startedAt || walk.completedAt || walk.attendanceValidatedAt || walk.archivedAt) {
                <section class="detail-section">
                  <h3>Suivi</h3>
                  <ul class="detail-timeline">
                    @if (walk.startedAt) {
                      <li>Démarrée {{ walk.startedAt | date: 'short' }}</li>
                    }
                    @if (walk.completedAt) {
                      <li>Terminée {{ walk.completedAt | date: 'short' }}</li>
                    }
                    @if (walk.attendanceValidatedAt) {
                      <li>Présences validées {{ walk.attendanceValidatedAt | date: 'short' }}</li>
                    }
                    @if (walk.archivedAt) {
                      <li>Archivée {{ walk.archivedAt | date: 'short' }}</li>
                    }
                  </ul>
                </section>
              }

              <section class="detail-section">
                <h3>
                  Participants
                  <span class="detail-count">{{ detailParticipants().length }}</span>
                </h3>
                @if (detailParticipants().length === 0) {
                  <p class="detail-empty">Aucun participant inscrit.</p>
                } @else {
                  <ul class="detail-participants">
                    @for (p of detailParticipants(); track p.bookingId) {
                      <li>
                        <div class="part-main">
                          <strong>{{ p.displayName }}</strong>
                          @if (detailRosterFull() && p.dogName) {
                            <span>{{ p.dogName }}</span>
                          }
                        </div>
                        @if (detailRosterFull()) {
                          <div class="part-meta">
                            <em>{{ bookingStatusLabel(p.bookingStatus) }}</em>
                            @if (p.phone) {
                              <a [href]="'tel:' + p.phone">{{ p.phone }}</a>
                            }
                          </div>
                        }
                      </li>
                    }
                  </ul>
                }
              </section>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .cal-page {
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
        min-height: calc(100dvh - var(--header-h) - var(--nav-h) - 1rem);
        padding: 0.75rem 0.75rem 1rem;
        max-width: 72rem;
        margin: 0 auto;
      }

      .cal-toolbar {
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
        position: sticky;
        top: 0;
        z-index: 5;
        background: color-mix(in srgb, var(--bg) 92%, transparent);
        backdrop-filter: blur(8px);
        padding-bottom: 0.25rem;
      }

      .section-label {
        font-size: 0.72rem;
        font-weight: 800;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--muted);
      }

      .toolbar-row {
        display: flex;
        align-items: center;
        gap: 0.45rem;
        flex-wrap: wrap;
      }

      .toolbar-row.secondary {
        justify-content: space-between;
      }

      .btn-today {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        border: 1px solid var(--line);
        background: var(--surface);
        border-radius: 999px;
        padding: 0.4rem 0.75rem;
        font-weight: 700;
        font-size: 0.82rem;
        cursor: pointer;
      }

      .btn-today svg {
        width: 1rem;
        height: 1rem;
      }

      .nav-arrows {
        display: inline-flex;
        gap: 0.2rem;
      }

      .icon-btn {
        width: 2rem;
        height: 2rem;
        border-radius: 999px;
        border: 1px solid var(--line);
        background: var(--surface);
        font-size: 1.15rem;
        line-height: 1;
        cursor: pointer;
      }

      .range-title {
        margin: 0;
        flex: 1;
        min-width: 0;
        font-size: 1rem;
        font-weight: 800;
        color: var(--forest-deep);
      }

      .view-toggle {
        display: inline-flex;
        padding: 0.15rem;
        border-radius: 999px;
        background: var(--surface-soft);
        border: 1px solid var(--line);
      }

      .view-toggle button {
        border: 0;
        background: transparent;
        border-radius: 999px;
        padding: 0.35rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 700;
        color: var(--muted);
        cursor: pointer;
      }

      .view-toggle button.active {
        background: var(--forest);
        color: var(--on-forest);
      }

      .hint {
        margin: 0;
        font-size: 0.75rem;
        color: var(--muted);
      }

      .legend {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
      }

      .legend-item {
        font-size: 0.68rem;
        font-weight: 700;
        padding: 0.2rem 0.5rem;
        border-radius: 999px;
        border: 1px solid transparent;
      }

      .legend-item[data-status='PUBLISHED'],
      .event[data-status='PUBLISHED'],
      .mobile-list li[data-status='PUBLISHED'] {
        background: #e5f3ea;
        border-color: #8fbf9a;
        color: #1a6b36;
      }
      .legend-item[data-status='FULL'],
      .event[data-status='FULL'],
      .mobile-list li[data-status='FULL'] {
        background: #fff1df;
        border-color: #e0a86a;
        color: #9a5400;
      }
      .legend-item[data-status='IN_PROGRESS'],
      .event[data-status='IN_PROGRESS'],
      .mobile-list li[data-status='IN_PROGRESS'] {
        background: #e7f0fb;
        border-color: #8ab0de;
        color: #1d4f91;
      }
      .legend-item[data-status='COMPLETED'],
      .event[data-status='COMPLETED'],
      .mobile-list li[data-status='COMPLETED'] {
        background: #e8eaf8;
        border-color: #9aa3d6;
        color: #3a3f8f;
      }
      .legend-item[data-status='CANCELLED'],
      .event[data-status='CANCELLED'],
      .mobile-list li[data-status='CANCELLED'] {
        background: #fbeaea;
        border-color: #e0a0a0;
        color: #9b2c2c;
      }
      .legend-item[data-status='DRAFT'],
      .event[data-status='DRAFT'],
      .mobile-list li[data-status='DRAFT'] {
        background: #f4f1ea;
        border-color: #d2c7b4;
        color: #6b5b3e;
      }
      .legend-item[data-status='ARCHIVED'],
      .event[data-status='ARCHIVED'],
      .mobile-list li[data-status='ARCHIVED'] {
        background: #f0ece6;
        border-color: #c4b8a8;
        color: #5c5346;
      }

      .state {
        margin: 1rem 0;
        color: var(--muted);
      }
      .state.error {
        color: var(--danger);
      }

      .cal-board {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        border: 1px solid var(--line);
        border-radius: 16px;
        background: var(--surface);
        overflow: hidden;
        box-shadow: var(--shadow-sm);
      }

      .cal-head {
        display: grid;
        grid-template-columns: 2.4rem 1fr;
        border-bottom: 1px solid var(--line);
        background: #fbfaf7;
      }

      .gutter-spacer {
        border-right: 1px solid var(--line);
      }

      .day-heads {
        display: grid;
        grid-template-columns: repeat(var(--cols, 1), minmax(0, 1fr));
      }

      .cal-board[data-cols='1'] {
        --cols: 1;
      }
      .cal-board[data-cols='7'] {
        --cols: 7;
      }

      .day-head {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.1rem;
        padding: 0.45rem 0.2rem;
        border: 0;
        border-right: 1px solid var(--line);
        background: transparent;
        cursor: pointer;
        min-width: 0;
      }

      .day-head:last-child {
        border-right: 0;
      }

      .day-num {
        font-size: 0.95rem;
        font-weight: 800;
        color: var(--ink);
      }

      .day-name {
        font-size: 0.68rem;
        font-weight: 700;
        text-transform: capitalize;
        color: var(--muted);
      }

      .day-head.today .day-num,
      .day-head.today .day-name {
        color: #1d4f91;
      }

      .day-head.today {
        box-shadow: inset 0 3px 0 #1d4f91;
      }

      .day-head.selected:not(.today) {
        background: var(--forest-soft);
      }

      .cal-body {
        position: relative;
        display: grid;
        grid-template-columns: 2.4rem 1fr;
        flex: 1;
        min-height: 28rem;
        max-height: min(68dvh, 42rem);
        overflow: auto;
        -webkit-overflow-scrolling: touch;
      }

      .hours-gutter {
        border-right: 1px solid var(--line);
        background: #fbfaf7;
      }

      .hour-label {
        box-sizing: border-box;
        padding: 0.15rem 0.25rem 0 0;
        font-size: 0.68rem;
        font-weight: 700;
        color: var(--muted);
        text-align: right;
        transform: translateY(-0.45rem);
      }

      .day-columns {
        position: relative;
        display: grid;
        grid-template-columns: repeat(var(--cols, 1), minmax(0, 1fr));
        min-height: calc(var(--hour-h, 6.2rem) * ${HOUR_COUNT});
      }

      .day-col {
        position: relative;
        border-right: 1px solid var(--line);
        min-width: 0;
      }

      .day-col:last-child {
        border-right: 0;
      }

      .day-col.today {
        background: color-mix(in srgb, #1d4f91 4%, transparent);
      }

      .hour-grid {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
      }

      .hour-slot {
        flex: 1;
        border-bottom: 1px solid var(--line);
        position: relative;
      }

      .hour-slot .half {
        position: absolute;
        left: 0;
        right: 0;
        top: 50%;
        border-top: 1px dashed color-mix(in srgb, var(--line) 80%, transparent);
      }

      .now-line {
        position: absolute;
        left: 0;
        right: 0;
        z-index: 3;
        height: 0;
        border-top: 2px dashed #3b82f6;
        pointer-events: none;
      }

      .now-dot {
        position: absolute;
        left: 0;
        top: -5px;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: #3b82f6;
      }

      .event-stack {
        position: absolute;
        left: 0.15rem;
        right: 0.15rem;
        z-index: 2;
      }

      .event-stack.has-stack {
        right: 0.55rem;
      }

      .stack-back {
        position: absolute;
        inset: 0;
        border-radius: 8px;
        background: #dfe6df;
        border: 1px solid #c5d0c7;
        pointer-events: none;
      }

      .stack-back.back-1 {
        transform: translate(3px, 3px);
        opacity: 0.9;
      }

      .stack-back.back-2 {
        transform: translate(6px, 6px);
        opacity: 0.65;
      }

      .event {
        position: relative;
        z-index: 2;
        display: flex;
        flex-direction: column;
        gap: 0.05rem;
        width: 100%;
        height: 100%;
        padding: 0.25rem 0.35rem;
        border-radius: 8px;
        border: 1px solid transparent;
        overflow: hidden;
        text-align: left;
        font: inherit;
        color: inherit;
        cursor: pointer;
        box-shadow: var(--shadow-sm);
        min-height: 1.6rem;
        box-sizing: border-box;
      }

      .event.compact {
        padding-right: 2rem;
      }

      .event.compact .ev-title {
        -webkit-line-clamp: 1;
      }

      .stack-badge {
        position: absolute;
        top: 0.2rem;
        right: 0.2rem;
        z-index: 3;
        min-width: 1.55rem;
        height: 1.35rem;
        padding: 0 0.35rem;
        border: 0;
        border-radius: 999px;
        background: var(--forest-deep);
        color: #fff;
        font-size: 0.68rem;
        font-weight: 800;
        cursor: pointer;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
      }

      .stack-badge:hover {
        background: var(--forest);
      }

      .ev-time {
        font-size: 0.62rem;
        font-weight: 800;
        opacity: 0.9;
      }

      .ev-title {
        font-size: 0.72rem;
        font-weight: 800;
        line-height: 1.15;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .ev-meta {
        font-size: 0.6rem;
        font-weight: 600;
        opacity: 0.85;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .sheet-backdrop {
        position: fixed;
        inset: 0;
        z-index: 80;
        background: rgba(26, 31, 26, 0.35);
      }

      .cluster-sheet {
        position: fixed;
        left: 0.75rem;
        right: 0.75rem;
        bottom: calc(var(--nav-h) + 0.75rem + env(safe-area-inset-bottom, 0px));
        z-index: 81;
        max-height: min(60dvh, 28rem);
        display: flex;
        flex-direction: column;
        border-radius: 18px;
        background: #fff;
        border: 1px solid var(--line);
        box-shadow: 0 16px 40px rgba(31, 61, 34, 0.18);
        overflow: hidden;
      }

      .sheet-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.9rem 1rem 0.7rem;
        border-bottom: 1px solid var(--line);
      }

      .sheet-head h2 {
        margin: 0;
        font-size: 1rem;
      }

      .sheet-head p {
        margin: 0.15rem 0 0;
        font-size: 0.78rem;
        color: var(--muted);
      }

      .sheet-close {
        width: 2rem;
        height: 2rem;
        border: 0;
        border-radius: 999px;
        background: var(--surface-soft);
        font-size: 1.25rem;
        line-height: 1;
        cursor: pointer;
      }

      .sheet-list {
        list-style: none;
        margin: 0;
        padding: 0.55rem;
        overflow: auto;
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }

      .sheet-list li {
        border-radius: 12px;
        border: 1px solid transparent;
        overflow: hidden;
      }

      .sheet-list button {
        display: grid;
        gap: 0.1rem;
        width: 100%;
        padding: 0.7rem 0.8rem;
        border: 0;
        background: transparent;
        text-align: left;
        font: inherit;
        color: inherit;
        cursor: pointer;
      }

      .sheet-list strong {
        font-size: 0.78rem;
      }

      .sheet-list span {
        font-weight: 800;
        font-size: 0.92rem;
      }

      .sheet-list em {
        font-style: normal;
        font-size: 0.75rem;
        opacity: 0.85;
      }

      .sheet-list li[data-status='PUBLISHED'] {
        background: #e5f3ea;
        border-color: #8fbf9a;
        color: #1a6b36;
      }
      .sheet-list li[data-status='FULL'] {
        background: #fff1df;
        border-color: #e0a86a;
        color: #9a5400;
      }
      .sheet-list li[data-status='IN_PROGRESS'] {
        background: #e7f0fb;
        border-color: #8ab0de;
        color: #1d4f91;
      }
      .sheet-list li[data-status='COMPLETED'] {
        background: #e8eaf8;
        border-color: #9aa3d6;
        color: #3a3f8f;
      }
      .sheet-list li[data-status='CANCELLED'] {
        background: #fbeaea;
        border-color: #e0a0a0;
        color: #9b2c2c;
      }
      .sheet-list li[data-status='DRAFT'] {
        background: #f4f1ea;
        border-color: #d2c7b4;
        color: #6b5b3e;
      }
      .sheet-list li[data-status='ARCHIVED'] {
        background: #f0ece6;
        border-color: #c4b8a8;
        color: #5c5346;
      }

      .mobile-list {
        display: block;
      }

      .mobile-list h2 {
        margin: 0.35rem 0;
        font-size: 0.95rem;
      }

      .mobile-list ul {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
      }

      .mobile-list li {
        border-radius: 12px;
        border: 1px solid transparent;
        overflow: hidden;
      }

      .mobile-list button {
        display: grid;
        gap: 0.1rem;
        width: 100%;
        padding: 0.7rem 0.8rem;
        border: 0;
        background: transparent;
        text-align: left;
        font: inherit;
        color: inherit;
        cursor: pointer;
      }

      .mobile-list strong {
        font-size: 0.78rem;
      }

      .mobile-list span {
        font-weight: 800;
        font-size: 0.92rem;
      }

      .mobile-list em {
        font-style: normal;
        font-size: 0.75rem;
        opacity: 0.85;
      }

      @media (min-width: 720px) {
        .cal-page {
          padding: 1rem 1.25rem 1.5rem;
        }

        .range-title {
          font-size: 1.15rem;
        }

        .mobile-list {
          display: none;
        }

        .cal-body {
          max-height: min(72dvh, 48rem);
        }

        .day-columns {
          min-height: calc(6.7rem * ${HOUR_COUNT});
        }

        .event-stack {
          left: 0.25rem;
          right: 0.25rem;
        }

        .event-stack.has-stack {
          right: 0.7rem;
        }

        .event {
          padding: 0.35rem 0.45rem;
        }

        .ev-title {
          font-size: 0.78rem;
        }

        .cluster-sheet {
          left: 50%;
          right: auto;
          width: min(26rem, calc(100vw - 2rem));
          transform: translateX(-50%);
          bottom: 1.5rem;
        }
      }

      .detail-backdrop {
        position: fixed;
        inset: 0;
        z-index: 90;
        background: rgba(26, 31, 26, 0.45);
      }

      .detail-modal {
        position: fixed;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
        z-index: 91;
        width: min(28rem, calc(100vw - 1.5rem));
        max-height: min(90dvh, 40rem);
        display: flex;
        flex-direction: column;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-md);
        overflow: hidden;
      }

      .detail-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 1.1rem 1.15rem 0.75rem;
        flex-shrink: 0;
      }

      .detail-kicker {
        margin: 0;
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--muted);
      }

      .detail-head h2 {
        margin: 0.2rem 0 0;
        font-size: 1.15rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
        line-height: 1.25;
      }

      .detail-head .sheet-close {
        flex-shrink: 0;
        margin-top: 0.1rem;
      }

      .detail-state {
        margin: 0 1.15rem 1.15rem;
        color: var(--muted);
      }

      .detail-state.error {
        color: var(--danger);
      }

      .detail-body {
        overflow: auto;
        padding: 0 1.15rem 1.25rem;
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
      }

      .detail-hero {
        width: 100%;
        aspect-ratio: 16 / 9;
        max-height: 11rem;
        object-fit: cover;
        border-radius: var(--radius-sm);
        border: 1px solid var(--line);
      }

      .detail-title-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
      }

      .detail-status {
        display: inline-flex;
        align-items: center;
        padding: 0.2rem 0.55rem;
        border-radius: var(--radius-pill);
        border: 1px solid transparent;
        font-size: 0.72rem;
        font-weight: 800;
      }

      .detail-status[data-status='PUBLISHED'] {
        background: #e5f3ea;
        border-color: #8fbf9a;
        color: #1a6b36;
      }
      .detail-status[data-status='FULL'] {
        background: #fff1df;
        border-color: #e0a86a;
        color: #9a5400;
      }
      .detail-status[data-status='IN_PROGRESS'] {
        background: #e7f0fb;
        border-color: #8ab0de;
        color: #1d4f91;
      }
      .detail-status[data-status='COMPLETED'] {
        background: #e8eaf8;
        border-color: #9aa3d6;
        color: #3a3f8f;
      }
      .detail-status[data-status='CANCELLED'] {
        background: #fbeaea;
        border-color: #e0a0a0;
        color: #9b2c2c;
      }
      .detail-status[data-status='DRAFT'] {
        background: #f4f1ea;
        border-color: #d2c7b4;
        color: #6b5b3e;
      }
      .detail-status[data-status='ARCHIVED'] {
        background: #f0ece6;
        border-color: #c4b8a8;
        color: #5c5346;
      }

      .detail-grid {
        margin: 0;
        display: grid;
        grid-template-columns: 1fr;
        gap: 0.65rem;
      }

      .detail-grid div {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        padding-bottom: 0.55rem;
        border-bottom: 1px solid color-mix(in srgb, var(--line) 80%, transparent);
      }

      .detail-grid div:last-child {
        border-bottom: 0;
        padding-bottom: 0;
      }

      .detail-grid dt {
        font-size: 0.72rem;
        font-weight: 700;
        color: var(--muted);
      }

      .detail-grid dd {
        margin: 0;
        font-size: 0.92rem;
        font-weight: 600;
        color: var(--forest-deep);
        line-height: 1.35;
        overflow-wrap: anywhere;
      }

      .detail-sub {
        display: block;
        margin-top: 0.15rem;
        font-size: 0.8rem;
        font-weight: 500;
        color: var(--muted);
      }

      .detail-diff {
        padding-top: 0.15rem;
      }

      .detail-section h3 {
        margin: 0 0 0.45rem;
        font-size: 0.92rem;
        color: var(--forest-deep);
        display: flex;
        align-items: center;
        gap: 0.4rem;
      }

      .detail-section p {
        margin: 0;
        font-size: 0.9rem;
        line-height: 1.45;
        color: var(--ink);
        white-space: pre-wrap;
      }

      .detail-count {
        display: inline-flex;
        min-width: 1.35rem;
        height: 1.2rem;
        padding: 0 0.35rem;
        align-items: center;
        justify-content: center;
        border-radius: var(--radius-pill);
        background: var(--surface-soft);
        font-size: 0.72rem;
        font-weight: 800;
      }

      .detail-chips {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
      }

      .detail-chips li {
        padding: 0.25rem 0.55rem;
        border-radius: var(--radius-pill);
        background: var(--surface-soft);
        border: 1px solid var(--line);
        font-size: 0.75rem;
        font-weight: 700;
      }

      .detail-timeline {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        font-size: 0.86rem;
      }

      .detail-empty {
        margin: 0;
        font-size: 0.86rem;
        color: var(--muted);
      }

      .detail-participants {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
      }

      .detail-participants li {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        padding: 0.7rem 0.8rem;
        border-radius: var(--radius-sm);
        border: 1px solid var(--line);
        background: var(--surface-soft);
      }

      .part-main {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.5rem;
      }

      .part-main strong {
        font-size: 0.92rem;
        color: var(--forest-deep);
      }

      .part-main span {
        font-size: 0.84rem;
        font-weight: 700;
      }

      .part-meta {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.55rem;
        font-size: 0.78rem;
      }

      .part-meta em {
        font-style: normal;
        font-weight: 700;
        color: var(--muted);
      }

      .part-meta a {
        color: var(--forest);
        font-weight: 700;
        text-decoration: none;
      }

      @media (min-width: 560px) {
        .detail-grid {
          grid-template-columns: 1fr 1fr;
          gap: 0.55rem 1rem;
        }

        .detail-grid div {
          border-bottom: 0;
          padding-bottom: 0;
        }
      }

      @media (min-width: 1100px) {
        .cal-head,
        .cal-body {
          grid-template-columns: 3rem 1fr;
        }
      }
    `,
  ],
})
export class CalendarComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly walkTypeLabel = walkTypeLabel;
  protected readonly sizeLabel = sizeLabel;
  protected readonly energyLabel = energyLabel;
  protected readonly bookingStatusLabel = bookingStatusLabel;
  protected readonly hourCount = HOUR_COUNT;
  protected readonly hours = Array.from({ length: HOUR_COUNT }, (_, i) => DAY_START_HOUR + i);

  protected readonly legend = [
    { key: 'PUBLISHED', label: 'Publiée' },
    { key: 'FULL', label: 'Complète' },
    { key: 'IN_PROGRESS', label: 'En cours' },
    { key: 'COMPLETED', label: 'Terminée' },
    { key: 'CANCELLED', label: 'Annulée' },
    { key: 'DRAFT', label: 'Brouillon' },
    { key: 'ARCHIVED', label: 'Archivée' },
  ];

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly walks = signal<CalWalk[]>([]);
  protected readonly anchor = signal(startOfDay(new Date()));
  protected readonly viewMode = signal<'day' | 'week'>('day');
  protected readonly now = signal(new Date());
  protected readonly clusterSheet = signal<EventCluster | null>(null);
  protected readonly detailOpen = signal(false);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);
  protected readonly detailWalk = signal<WalkDetail | null>(null);
  protected readonly detailParticipants = signal<WalkParticipant[]>([]);
  /** true = liste staff (chien / statut / tél.) ; false = pseudos seuls. */
  protected readonly detailRosterFull = signal(false);

  protected readonly days = computed<DayColumn[]>(() => {
    const mode = this.viewMode();
    const anchor = this.anchor();
    if (mode === 'day') {
      return [this.toDayColumn(anchor)];
    }
    const weekStart = startOfWeek(anchor, { weekStartsOn: 1 });
    return Array.from({ length: 7 }, (_, i) => this.toDayColumn(addDays(weekStart, i)));
  });

  protected readonly rangeLabel = computed(() => {
    const cols = this.days();
    if (cols.length === 1) {
      return format(cols[0].date, 'EEEE d MMMM yyyy', { locale: fr });
    }
    const first = cols[0].date;
    const last = cols[cols.length - 1].date;
    if (first.getMonth() === last.getMonth()) {
      return `${format(first, 'd', { locale: fr })}–${format(last, 'd MMMM yyyy', { locale: fr })}`;
    }
    return `${format(first, 'd MMM', { locale: fr })} – ${format(last, 'd MMM yyyy', { locale: fr })}`;
  });

  protected readonly mobileDayList = computed(() => this.eventsForDay(this.anchor()));

  ngOnInit(): void {
    this.applyResponsiveDefault();
    fromEvent(window, 'resize')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyResponsiveDefault(false));
    fromEvent<KeyboardEvent>(window, 'keydown')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((e) => {
        if (e.key !== 'Escape') {
          return;
        }
        if (this.detailOpen()) {
          this.closeWalkDetail();
          return;
        }
        this.closeClusterSheet();
      });

    const tick = window.setInterval(() => this.now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));

    this.http.get<CalWalk[]>('/api/walks/calendar').subscribe({
      next: (list) => {
        this.walks.set(list ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger l’agenda.');
        this.loading.set(false);
      },
    });
  }

  protected setView(mode: 'day' | 'week'): void {
    this.viewMode.set(mode);
    this.closeClusterSheet();
  }

  protected goToday(): void {
    this.anchor.set(startOfDay(new Date()));
    this.closeClusterSheet();
  }

  protected shift(dir: -1 | 1): void {
    const current = this.anchor();
    this.anchor.set(
      this.viewMode() === 'day' ? addDays(current, dir) : addWeeks(current, dir),
    );
    this.closeClusterSheet();
  }

  protected focusDay(date: Date): void {
    this.anchor.set(startOfDay(date));
    this.closeClusterSheet();
    if (window.matchMedia('(max-width: 719px)').matches) {
      this.viewMode.set('day');
    }
  }

  protected openClusterSheet(event: MouseEvent, cluster: EventCluster): void {
    event.preventDefault();
    event.stopPropagation();
    this.clusterSheet.set(cluster);
  }

  protected closeClusterSheet(): void {
    this.clusterSheet.set(null);
  }

  protected openWalkDetail(walkId: number): void {
    this.closeClusterSheet();
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.detailError.set(null);
    this.detailWalk.set(null);
    this.detailParticipants.set([]);
    this.detailRosterFull.set(false);

    this.http.get<WalkDetail>(`/api/walks/${walkId}`).subscribe({
      next: (walk) => {
        this.detailWalk.set(walk);
        this.detailLoading.set(false);
        this.http
          .get<{ walkId: number; participants: WalkParticipant[] }>(`/api/walks/${walkId}/participants`)
          .subscribe({
            next: (res) => {
              this.detailParticipants.set(res?.participants ?? []);
              this.detailRosterFull.set(true);
            },
            error: () => {
              const names = walk.participantDisplayNames ?? [];
              this.detailParticipants.set(
                names.map((displayName, i) => ({
                  bookingId: -1 - i,
                  userId: 0,
                  dogId: 0,
                  displayName,
                  dogName: '',
                  bookingStatus: '',
                  phone: null,
                })),
              );
              this.detailRosterFull.set(false);
            },
          });
      },
      error: () => {
        this.detailError.set('Impossible de charger le détail de la balade.');
        this.detailLoading.set(false);
      },
    });
  }

  protected closeWalkDetail(): void {
    this.detailOpen.set(false);
    this.detailWalk.set(null);
    this.detailParticipants.set([]);
    this.detailRosterFull.set(false);
    this.detailError.set(null);
    this.detailLoading.set(false);
  }

  protected detailColorKey(walk: WalkDetail): string {
    return this.colorKey({
      id: walk.id,
      title: walk.title,
      cityName: walk.cityName,
      walkType: walk.walkType,
      startsAt: walk.startsAt,
      durationMinutes: walk.durationMinutes,
      capacity: walk.capacity,
      bookedCount: walk.bookedCount,
      status: walk.status,
      archivedAt: walk.archivedAt,
    });
  }

  protected detailStatusLabel(walk: WalkDetail): string {
    const key = this.detailColorKey(walk);
    if (key === 'FULL') {
      return 'Complète';
    }
    if (key === 'ARCHIVED') {
      return 'Archivée';
    }
    return walkStatusLabel(walk.status);
  }

  protected formatWalkWhen(iso: string): string {
    return format(new Date(iso), "EEEE d MMMM yyyy · HH:mm", { locale: fr });
  }

  protected isSelectedDay(date: Date): boolean {
    return isSameDay(date, this.anchor());
  }

  protected hintText(): string {
    return this.viewMode() === 'day'
      ? 'Vue jour — passez en semaine sur grand écran'
      : 'Vue semaine — lun. → dim.';
  }

  protected showNowLine(): boolean {
    return this.days().some((d) => d.isToday);
  }

  protected nowTopPct(): number {
    const n = this.now();
    const minutes = n.getHours() * 60 + n.getMinutes();
    const start = DAY_START_HOUR * 60;
    const end = DAY_END_HOUR * 60;
    const clamped = Math.min(end, Math.max(start, minutes));
    return ((clamped - start) / (end - start)) * 100;
  }

  protected eventsForDay(date: Date): PositionedEvent[] {
    const dayStart = startOfDay(date);
    const dayEnd = addDays(dayStart, 1);
    return this.walks()
      .map((walk) => {
        const start = new Date(walk.startsAt);
        const end = new Date(start.getTime() + Math.max(30, walk.durationMinutes) * 60_000);
        if (end <= dayStart || start >= dayEnd) {
          return null;
        }
        const visibleStart = start < dayStart ? dayStart : start;
        const visibleEnd = end > dayEnd ? dayEnd : end;
        const topPct = this.pctOfDay(visibleStart);
        const endPct = this.pctOfDay(visibleEnd);
        const heightPct = Math.max(2.2, endPct - topPct);
        const colorKey = this.colorKey(walk);
        return {
          walk,
          topPct,
          heightPct,
          startMs: start.getTime(),
          endMs: end.getTime(),
          colorKey,
          label: this.statusLabel(walk, colorKey),
          timeLabel: format(start, 'HH:mm'),
        } satisfies PositionedEvent;
      })
      .filter((e): e is PositionedEvent => e != null)
      .sort((a, b) => a.startMs - b.startMs || a.walk.id - b.walk.id);
  }

  /** Regroupe les balades qui se chevauchent : 1 pastille visible + badge +N. */
  protected clustersForDay(date: Date): EventCluster[] {
    const events = this.eventsForDay(date);
    const clusters: { items: PositionedEvent[]; spanStart: number; spanEnd: number }[] = [];

    for (const ev of events) {
      let merged = false;
      for (const cluster of clusters) {
        if (ev.startMs < cluster.spanEnd && ev.endMs > cluster.spanStart) {
          cluster.items.push(ev);
          cluster.spanStart = Math.min(cluster.spanStart, ev.startMs);
          cluster.spanEnd = Math.max(cluster.spanEnd, ev.endMs);
          merged = true;
          break;
        }
      }
      if (!merged) {
        clusters.push({
          items: [ev],
          spanStart: ev.startMs,
          spanEnd: ev.endMs,
        });
      }
    }

    // Fusionne les clusters devenus adjacents après extensions successives.
    let changed = true;
    while (changed) {
      changed = false;
      for (let i = 0; i < clusters.length; i++) {
        for (let j = i + 1; j < clusters.length; j++) {
          const a = clusters[i];
          const b = clusters[j];
          if (a.spanStart < b.spanEnd && a.spanEnd > b.spanStart) {
            a.items.push(...b.items);
            a.spanStart = Math.min(a.spanStart, b.spanStart);
            a.spanEnd = Math.max(a.spanEnd, b.spanEnd);
            clusters.splice(j, 1);
            changed = true;
            break;
          }
        }
        if (changed) {
          break;
        }
      }
    }

    return clusters.map((cluster) => {
      const items = [...cluster.items].sort(
        (a, b) => a.startMs - b.startMs || a.walk.id - b.walk.id,
      );
      const primary =
        items.find((e) => e.colorKey === 'IN_PROGRESS') ??
        items.find((e) => e.colorKey === 'PUBLISHED' || e.colorKey === 'FULL') ??
        items[0];
      const topPct = Math.min(...items.map((e) => e.topPct));
      const bottomPct = Math.max(...items.map((e) => e.topPct + e.heightPct));
      return {
        id: `${format(date, 'yyyy-MM-dd')}-${primary.walk.id}-${items.length}`,
        primary,
        items,
        topPct,
        heightPct: Math.max(primary.heightPct, bottomPct - topPct),
        extraCount: Math.max(0, items.length - 1),
      } satisfies EventCluster;
    });
  }

  private pctOfDay(date: Date): number {
    const minutes = date.getHours() * 60 + date.getMinutes();
    const start = DAY_START_HOUR * 60;
    const end = DAY_END_HOUR * 60;
    const clamped = Math.min(end, Math.max(start, minutes));
    return ((clamped - start) / (end - start)) * 100;
  }

  private colorKey(walk: CalWalk): string {
    const status = (walk.status || '').toUpperCase();
    if (status === 'PUBLISHED' && walk.bookedCount >= walk.capacity && walk.capacity > 0) {
      return 'FULL';
    }
    switch (status) {
      case 'DRAFT':
      case 'PUBLISHED':
      case 'IN_PROGRESS':
      case 'COMPLETED':
      case 'CANCELLED':
        return status;
      default:
        return walk.archivedAt ? 'ARCHIVED' : 'PUBLISHED';
    }
  }

  private statusLabel(walk: CalWalk, colorKey: string): string {
    if (colorKey === 'FULL') {
      return 'Complète';
    }
    return walkStatusLabel(walk.status);
  }

  private toDayColumn(date: Date): DayColumn {
    return {
      date,
      key: format(date, 'yyyy-MM-dd'),
      dayNum: format(date, 'd'),
      weekday: format(date, 'EEE', { locale: fr }),
      isToday: isToday(date),
    };
  }

  private applyResponsiveDefault(force = true): void {
    if (!force) {
      return;
    }
    const wide = window.matchMedia('(min-width: 720px)').matches;
    this.viewMode.set(wide ? 'week' : 'day');
  }
}
