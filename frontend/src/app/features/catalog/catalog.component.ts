// Catalogue filtré par ville sélectionnée + carte des balades.
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import {
  AttendanceReminderService,
  PendingRollcallWalk,
} from '../../core/attendance-reminder.service';
import { AuthService } from '../../core/auth.service';
import { CityPickerComponent } from '../../shared/city-picker/city-picker.component';
import { City } from '../../shared/city.service';
import { CitySelectionService } from '../../shared/city-selection.service';
import {
  CommunityWalkEditModel,
  CommunityWalkModalComponent,
} from '../../shared/community-walk-modal/community-walk-modal.component';
import { RouteGeoJson } from '../../shared/route-editor/route-editor.component';
import { walkStatusLabel, walkTypeLabel } from '../../shared/labels';
import { WalkDifficultyBarComponent } from '../../shared/walk-difficulty-bar/walk-difficulty-bar.component';
import { WalkParticipantsComponent } from '../../shared/walk-participants/walk-participants.component';
import { WalkRouteBadgeComponent } from '../../shared/walk-route-badge/walk-route-badge.component';
import { WalksMapComponent } from '../../shared/walks-map/walks-map.component';

export interface Walk {
  id: number;
  title: string;
  description: string;
  walkType: string;
  cityId: number;
  cityName: string | null;
  department?: string | null;
  region?: string | null;
  meetingPointId: number;
  meetingPointName: string | null;
  meetingPointAddress: string | null;
  lat: number | null;
  lng: number | null;
  durationMinutes: number;
  elevationGainM?: number;
  terrainType?: string;
  difficultyLevel?: number;
  organizerDisplayName?: string | null;
  promoPhotoUrls?: string[] | null;
  recycledFromWalkId?: number | null;
  galleryCount?: number;
  priceCents: number;
  nonMemberPriceCents: number | null;
  capacity: number;
  bookedCount: number;
  startsAt: string;
  status: string;
  archivedAt?: string | null;
  organizerUserId?: number | null;
  acceptedSizes?: string[];
  acceptedEnergy?: string[];
  minSociabilityDogs?: string | null;
  routeHint?: string | null;
  routeGeojson?: RouteGeoJson | string | null;
  /** null = non visible pour le viewer ; sinon liste (souvent des pseudos). */
  participantDisplayNames?: string[] | null;
}

interface RosterEntry {
  bookingId: number;
  userId: number;
  displayName: string;
  dogId: number;
  dogName: string;
  bookingStatus: string;
  attendanceStatus?: string | null;
}

interface RosterResponse {
  walkId: number;
  title: string;
  startsAt: string;
  endsAt: string;
  attendanceValidatedAt?: string | null;
  entries: RosterEntry[];
}

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [
    CityPickerComponent,
    WalksMapComponent,
    CommunityWalkModalComponent,
    WalkDifficultyBarComponent,
    WalkRouteBadgeComponent,
    WalkParticipantsComponent,
    RouterLink,
    DatePipe,
    DecimalPipe,
  ],
  template: `
    <div class="catalog-page">
      <header class="page-header">
        <div class="page-header-row">
          <div>
            <h1>Promenades disponibles</h1>
            <p class="subtitle">Sélectionnez votre ville pour découvrir les promeneurs.</p>
          </div>
          @if (canCreateCommunityWalk()) {
            <button type="button" class="btn-create" (click)="openCreateModal()">
              Proposer une balade
            </button>
          }
        </div>
      </header>

      <div class="filter-bar">
        <app-city-picker [initialCity]="selectedCity()" (citySelected)="onCity($event)" />
      </div>

      @if (createHint()) {
        <p class="banner warn" role="status">{{ createHint() }}</p>
      }

      @if (attendance.pendingCount() > 0) {
        <section class="rollcall-block" aria-label="Présences à valider">
          <header class="rollcall-head">
            <div>
              <h2>Présences à valider</h2>
              <p class="muted">Cochez les participants présents — l’heure de balade est dépassée.</p>
            </div>
            <span class="archived-count blink">{{ attendance.pendingCount() }}</span>
          </header>
          <ul class="rollcall-list">
            @for (item of attendance.pending(); track item.walkId) {
              <li class="rollcall-item">
                <div class="rollcall-info">
                  <h3>{{ item.title }}</h3>
                  <p class="meta">
                    {{ item.cityName || 'Ville' }} · {{ item.startsAt | date: 'short' }} ·
                    {{ item.confirmedCount }} inscrit(s)
                  </p>
                </div>
                <button type="button" class="btn-book" (click)="openRollcall(item)">
                  Pointer
                </button>
              </li>
            }
          </ul>
        </section>
      }

      @if (auth.loggedIn() && (canOrganizeWalks() || myUpcomingWalks().length > 0)) {
        <section class="mine-block" aria-label="Mes balades à venir">
          <header class="mine-head">
            <div>
              <h2>Mes balades à venir</h2>
              <p class="muted">Balades que vous avez créées, encore à venir.</p>
            </div>
            @if (myUpcomingWalks().length) {
              <span class="archived-count">{{ myUpcomingWalks().length }}</span>
            }
          </header>
          @if (myUpcomingLoading()) {
            <p class="muted pad">Chargement…</p>
          } @else if (myUpcomingWalks().length === 0) {
            <p class="muted pad">Aucune balade à venir pour le moment.</p>
          } @else {
            <ul class="archived-list mine-list">
              @for (walk of myUpcomingWalks(); track walk.id) {
                <li class="archived-item">
                  @if (walk.promoPhotoUrls?.[0]; as thumb) {
                    <img class="walk-thumb sm" [src]="thumb" [alt]="walk.title" />
                  }
                  <div class="archived-info">
                    <h3>
                      {{ walk.title }}
                      <app-walk-route-badge [routeGeojson]="walk.routeGeojson" />
                    </h3>
                    <p class="meta">
                      {{ walk.cityName || 'Ville' }} · {{ walk.startsAt | date: 'short' }}
                    </p>
                    <div class="tags">
                      <span class="tag">{{ walkTypeLabel(walk.walkType) }}</span>
                      <span
                        class="tag status-pill"
                        [attr.data-status]="statusPillKey(walk)"
                      >{{ walkStatusLabel(walk.status) }}</span>
                      <span class="tag">{{ walk.bookedCount }}/{{ walk.capacity }}</span>
                    </div>
                    <app-walk-participants [names]="walk.participantDisplayNames" />
                  </div>
                  <div class="mine-actions">
                    @if (canLaunchWalk(walk)) {
                      <a class="btn-book" [routerLink]="['/walks', walk.id, 'live']">
                        {{ walk.status === 'IN_PROGRESS' ? 'Reprendre' : 'Lancer' }}
                      </a>
                    }
                    @if (canEditWalk(walk)) {
                      <button type="button" class="btn-edit" (click)="openEditModal(walk)">
                        Modifier
                      </button>
                    }
                    <a
                      class="btn-book ghost"
                      routerLink="/booking"
                      [queryParams]="{ walkId: walk.id }"
                      >Voir</a
                    >
                  </div>
                </li>
              }
            </ul>
          }
        </section>
      }

      @if (loading()) {
        <p class="state-msg">Chargement…</p>
      } @else if (!selectedCity()) {
        <div class="empty-state">
          <span class="empty-icon">🗺️</span>
          <p>Cherchez une ville pour voir les promenades disponibles.</p>
        </div>
      } @else if (walks().length === 0) {
        <div class="empty-state">
          <span class="empty-icon">😔</span>
          <p>
            Aucune promenade disponible à <strong>{{ selectedCity()?.name }}</strong> pour le
            moment.
          </p>
        </div>
      } @else {
        <section class="map-block" aria-label="Carte des balades à {{ selectedCity()?.name }}">
          <div class="map-head">
            <h2>Sur la carte</h2>
            <p class="muted">Pins autour de {{ selectedCity()?.name }}</p>
          </div>
          <app-walks-map
            [walks]="walks()"
            [fallbackCenter]="cityCenter()"
            [cityFocus]="true"
            [selectedWalkId]="pulsedWalkId()"
            (pinSelected)="onPinSelected($event)"
          />
        </section>

        <ul class="walk-list">
          @for (walk of walks(); track walk.id) {
            <li
              class="walk-card"
              [class.is-pulse]="pulsedWalkId() === walk.id"
              [attr.data-walk-id]="walk.id"
              (click)="onPinSelected(walk.id)"
            >
              @if (walk.promoPhotoUrls?.[0]; as thumb) {
                <img class="walk-thumb" [src]="thumb" [alt]="walk.title" />
              }
              <div class="walk-info">
                <h3>
                  {{ walk.title }}
                  <app-walk-route-badge [routeGeojson]="walk.routeGeojson" />
                </h3>
                <p>{{ walk.description }}</p>
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
                  <span class="tag">{{ walk.bookedCount }}/{{ walk.capacity }}</span>
                </div>
                <app-walk-participants [names]="walk.participantDisplayNames" />
              </div>
              <div class="walk-meta">
                <span class="price">{{ (walk.priceCents / 100) | number: '1.0-2' }} €</span>
                <span class="walker">{{ walk.startsAt | date: 'short' }}</span>
                @if (canEditWalk(walk)) {
                  <button
                    type="button"
                    class="btn-edit"
                    (click)="openEditModal(walk); $event.stopPropagation()"
                  >
                    Modifier
                  </button>
                }
                <a
                  class="btn-book"
                  routerLink="/booking"
                  [queryParams]="{ walkId: walk.id }"
                  (click)="$event.stopPropagation()"
                  >Réserver</a
                >
              </div>
            </li>
          }
        </ul>
      }

      @if (auth.isAdmin()) {
        <section class="archived-block admin-archived" aria-label="Toutes les balades (admin)">
          <header class="archived-block-title">
            <h2>Toutes les balades</h2>
            <p class="muted">
              Classées par région puis département
              @if (selectedCity(); as city) {
                · filtre ville : {{ city.name }}
              }
            </p>
            @if (adminWalks().length) {
              <span class="archived-count">{{ adminWalks().length }}</span>
            }
          </header>

          <div class="status-filters" role="group" aria-label="Filtrer par statut">
            @for (opt of adminStatusOptions; track opt.value) {
              <button
                type="button"
                class="status-chip"
                [attr.data-status]="opt.value || 'ALL'"
                [class.active]="adminStatusFilter() === opt.value"
                (click)="setAdminStatusFilter(opt.value)"
              >
                {{ opt.label }}
              </button>
            }
          </div>

          @if (adminLoading()) {
            <p class="muted pad">Chargement…</p>
          } @else if (adminError()) {
            <p class="muted pad">{{ adminError() }}</p>
          } @else if (adminByRegion().length === 0) {
            <p class="muted pad">Aucune balade pour ce filtre.</p>
          } @else {
            <div class="region-stack">
              @for (region of adminByRegion(); track region.name) {
                <article class="region-tile" [class.is-open]="isRegionOpen(region.name)">
                  <button
                    type="button"
                    class="region-head fold-head"
                    (click)="toggleRegion(region.name)"
                    [attr.aria-expanded]="isRegionOpen(region.name)"
                  >
                    <span class="fold-chevron" aria-hidden="true">›</span>
                    <h3>{{ region.name }}</h3>
                    <span class="archived-count">{{ region.count }}</span>
                  </button>
                  @if (isRegionOpen(region.name)) {
                    <div class="dept-stack">
                      @for (dept of region.departments; track dept.code) {
                        <section
                          class="dept-tile"
                          [class.is-open]="isDeptOpen(region.name, dept.code)"
                        >
                          <button
                            type="button"
                            class="dept-head fold-head"
                            (click)="toggleDept(region.name, dept.code)"
                            [attr.aria-expanded]="isDeptOpen(region.name, dept.code)"
                          >
                            <span class="fold-chevron" aria-hidden="true">›</span>
                            <h4>Département {{ dept.code }}</h4>
                            <span class="archived-count sm">{{ dept.walks.length }}</span>
                          </button>
                          @if (isDeptOpen(region.name, dept.code)) {
                            <ul class="archived-list">
                              @for (walk of dept.walks; track walk.id) {
                                <li class="archived-item">
                                  @if (walk.promoPhotoUrls?.[0]; as thumb) {
                                    <img class="walk-thumb sm" [src]="thumb" [alt]="walk.title" />
                                  }
                                  <div class="archived-info">
                                    <h3>
                                      {{ walk.title }}
                                      <app-walk-route-badge [routeGeojson]="walk.routeGeojson" />
                                    </h3>
                                    <p class="meta">
                                      {{ walk.cityName || 'Ville' }}
                                      @if (walk.organizerDisplayName) {
                                        · {{ walk.organizerDisplayName }}
                                      }
                                      · {{ walk.startsAt | date: 'short' }}
                                      @if (walk.archivedAt) {
                                        · archivée
                                      }
                                    </p>
                                    <div class="tags">
                                      <span class="tag">{{ walkTypeLabel(walk.walkType) }}</span>
                                      <span
                                        class="tag status-pill"
                                        [attr.data-status]="statusPillKey(walk)"
                                      >{{ walkStatusLabel(walk.status) }}</span>
                                    </div>
                                    <app-walk-participants [names]="walk.participantDisplayNames" />
                                  </div>
                                  <a
                                    class="btn-book ghost"
                                    routerLink="/booking"
                                    [queryParams]="{ walkId: walk.id }"
                                    >Voir</a
                                  >
                                </li>
                              }
                            </ul>
                          }
                        </section>
                      }
                    </div>
                  }
                </article>
              }
            </div>
          }
        </section>
      } @else if (selectedCity()) {
        <section class="archived-block" aria-label="Balades archivées">
          <div class="archived-tile">
            <header class="archived-head">
              <div>
                <h2>Balades archivées</h2>
                <p class="muted">
                  Passées ou retirées du catalogue à {{ selectedCity()?.name }}.
                </p>
              </div>
              @if (archived().length) {
                <span class="archived-count">{{ archived().length }}</span>
              }
            </header>

            @if (!auth.loggedIn()) {
              <p class="muted pad">
                <a routerLink="/login">Connectez-vous</a> pour consulter les balades archivées.
              </p>
            } @else if (archivedLoading()) {
              <p class="muted pad">Chargement…</p>
            } @else if (archivedError()) {
              <p class="muted pad">{{ archivedError() }}</p>
            } @else if (archived().length === 0) {
              <p class="muted pad">Aucune balade archivée pour cette ville.</p>
            } @else {
              <ul class="archived-list">
                @for (walk of archived(); track walk.id) {
                  <li class="archived-item">
                    @if (walk.promoPhotoUrls?.[0]; as thumb) {
                      <img class="walk-thumb sm" [src]="thumb" [alt]="walk.title" />
                    }
                    <div class="archived-info">
                      <h3>
                        {{ walk.title }}
                        <app-walk-route-badge [routeGeojson]="walk.routeGeojson" />
                      </h3>
                      <p class="meta">
                        @if (walk.organizerDisplayName) {
                          {{ walk.organizerDisplayName }} ·
                        }
                        {{ walk.startsAt | date: 'short' }}
                        @if (walk.archivedAt) {
                          · archivée {{ walk.archivedAt | date: 'shortDate' }}
                        }
                      </p>
                      <div class="tags">
                        <span class="tag">{{ walkTypeLabel(walk.walkType) }}</span>
                        <span
                          class="tag status-pill"
                          [attr.data-status]="statusPillKey(walk)"
                        >{{ walkStatusLabel(walk.status) }}</span>
                        @if (walk.difficultyLevel) {
                          <span class="tag tag-diff">
                            <app-walk-difficulty-bar [level]="walk.difficultyLevel" />
                          </span>
                        }
                      </div>
                      <app-walk-participants [names]="walk.participantDisplayNames" />
                    </div>
                    <a
                      class="btn-book ghost"
                      routerLink="/booking"
                      [queryParams]="{ walkId: walk.id }"
                      >Voir</a
                    >
                  </li>
                }
              </ul>
            }
          </div>
        </section>
      }
    </div>

    @if (createOpen() && modalCity(); as city) {
      <app-community-walk-modal
        [city]="city"
        [walk]="editingWalk()"
        (closed)="closeCreateModal()"
        (saved)="onWalkModalSaved()"
      />
    }

    @if (rollcallOpen(); as roster) {
      <div class="rollcall-overlay" role="dialog" aria-modal="true" aria-labelledby="rollcall-title">
        <div class="rollcall-panel">
          <header class="rollcall-panel-head">
            <div>
              <h2 id="rollcall-title">Présents — {{ roster.title }}</h2>
              <p class="muted">
                Fin prévue {{ roster.endsAt | date: 'short' }}. Cochez les présents, laissez décoché =
                absent.
              </p>
            </div>
            <button type="button" class="btn-edit" (click)="closeRollcall()">Fermer</button>
          </header>
          @if (rollcallError()) {
            <p class="banner warn" role="alert">{{ rollcallError() }}</p>
          }
          @if (rollcallLoading()) {
            <p class="muted pad">Chargement…</p>
          } @else {
            <ul class="roster-list">
              @for (entry of roster.entries; track entry.bookingId) {
                <li class="roster-row">
                  <label class="roster-check">
                    <input
                      type="checkbox"
                      [checked]="rollcallPresent()[entry.bookingId] === true"
                      (change)="togglePresent(entry.bookingId, $event)"
                    />
                    <span class="roster-name">{{ formatParticipant(entry.displayName) }}</span>
                    <span class="roster-dog">{{ entry.dogName }}</span>
                  </label>
                </li>
              }
            </ul>
            <div class="rollcall-actions">
              <button
                type="button"
                class="btn-book"
                [disabled]="rollcallSaving() || roster.entries.length === 0"
                (click)="submitRollcall()"
              >
                {{ rollcallSaving() ? 'Enregistrement…' : 'Valider les présences' }}
              </button>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      .catalog-page {
        max-width: 28rem;
        margin: 0 auto;
        padding: 1.1rem 1rem 1.5rem;
      }
      .page-header {
        margin-bottom: 1.1rem;
      }
      .page-header-row {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
        color: var(--forest-deep);
        font-size: 1.45rem;
      }
      .subtitle {
        color: var(--muted);
        margin: 0;
        font-size: 0.92rem;
      }
      .filter-bar {
        margin-bottom: 1rem;
        max-width: 100%;
      }
      .rollcall-block {
        margin: 0 0 1.2rem;
        background: color-mix(in srgb, var(--cta) 8%, #fff);
        border: 1px solid color-mix(in srgb, var(--cta) 35%, var(--line));
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .rollcall-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.85rem 1rem 0.6rem;
        border-bottom: 1px solid color-mix(in srgb, var(--cta) 25%, var(--line));
      }
      .rollcall-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.05rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .rollcall-head .muted {
        margin: 0;
        font-size: 0.82rem;
      }
      .archived-count.blink {
        background: var(--cta);
        color: #fff;
        animation: nav-attn-blink 1.1s ease-in-out infinite;
      }
      @keyframes nav-attn-blink {
        0%,
        100% {
          opacity: 1;
        }
        50% {
          opacity: 0.45;
        }
      }
      .rollcall-list {
        list-style: none;
        margin: 0;
        padding: 0.25rem 0;
      }
      .rollcall-item {
        display: flex;
        align-items: center;
        gap: 0.65rem;
        padding: 0.7rem 1rem;
        border-bottom: 1px solid var(--line);
      }
      .rollcall-item:last-child {
        border-bottom: none;
      }
      .rollcall-info {
        flex: 1;
        min-width: 0;
      }
      .rollcall-info h3 {
        margin: 0 0 0.2rem;
        font-size: 0.95rem;
        color: var(--forest-deep);
      }
      .rollcall-overlay {
        position: fixed;
        inset: 0;
        z-index: 80;
        background: rgba(26, 31, 26, 0.45);
        display: flex;
        align-items: flex-end;
        justify-content: center;
        padding: 1rem;
      }
      .rollcall-panel {
        width: min(28rem, 100%);
        max-height: min(85dvh, 40rem);
        overflow: auto;
        background: var(--surface);
        border-radius: var(--radius-md) var(--radius-md) 0 0;
        box-shadow: var(--shadow-md);
        padding: 1rem;
      }
      .rollcall-panel-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        margin-bottom: 0.75rem;
      }
      .rollcall-panel-head h2 {
        margin: 0 0 0.25rem;
        font-size: 1.1rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .roster-list {
        list-style: none;
        margin: 0 0 1rem;
        padding: 0;
        display: grid;
        gap: 0.45rem;
      }
      .roster-row {
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
      }
      .roster-check {
        display: grid;
        grid-template-columns: auto 1fr;
        grid-template-rows: auto auto;
        column-gap: 0.65rem;
        row-gap: 0.1rem;
        align-items: center;
        padding: 0.65rem 0.75rem;
        cursor: pointer;
      }
      .roster-check input {
        grid-row: 1 / span 2;
        width: 1.1rem;
        height: 1.1rem;
        accent-color: var(--forest);
      }
      .roster-name {
        font-weight: 700;
        color: var(--forest-deep);
        font-size: 0.92rem;
      }
      .roster-dog {
        font-size: 0.78rem;
        color: var(--muted);
      }
      .rollcall-actions {
        display: flex;
        justify-content: stretch;
      }
      .rollcall-actions .btn-book {
        width: 100%;
        text-align: center;
      }
      .mine-block {
        margin: 0 0 1.2rem;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .mine-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.85rem 1rem 0.6rem;
        border-bottom: 1px solid var(--line);
        background: linear-gradient(180deg, var(--bg) 0%, transparent 100%);
      }
      .mine-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.05rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .mine-head .muted {
        margin: 0;
        font-size: 0.82rem;
      }
      .mine-list {
        padding: 0.15rem 0;
      }
      .mine-actions {
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: 0.35rem;
        flex-shrink: 0;
      }
      .mine-actions .btn-book,
      .mine-actions .btn-edit {
        text-align: center;
      }
      .banner.warn {
        margin: 0 0 0.85rem;
        padding: 0.55rem 0.7rem;
        border-radius: var(--radius-sm);
        background: #f8efe3;
        color: var(--warn);
        font-size: 0.88rem;
      }
      .btn-create {
        flex-shrink: 0;
        white-space: nowrap;
        background: var(--forest);
        color: var(--on-forest);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.55rem 1.1rem;
        font-size: 0.9rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
        box-shadow: var(--shadow-sm);
      }
      .btn-create:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-create:hover:not(:disabled) {
        filter: brightness(1.05);
      }
      .state-msg {
        color: var(--muted);
        text-align: center;
        padding: 2rem 0;
      }
      .empty-state {
        text-align: center;
        padding: 2.5rem 1rem;
        color: var(--muted);
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
      }
      .empty-icon {
        font-size: 2rem;
        display: block;
        margin-bottom: 0.75rem;
      }
      .map-block {
        margin-bottom: 1.1rem;
      }
      .map-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.05rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .map-head .muted {
        margin: 0 0 0.55rem;
        font-size: 0.85rem;
        color: var(--muted);
      }
      .walk-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
      }
      .walk-card {
        background: var(--surface);
        border-radius: var(--radius-md);
        border: 1px solid var(--line);
        padding: 1.05rem;
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        box-shadow: var(--shadow-sm);
      }
      .walk-thumb {
        width: 4.5rem;
        height: 4.5rem;
        object-fit: cover;
        border-radius: var(--radius-sm);
        flex-shrink: 0;
        background: var(--bg);
      }
      .walk-card.is-pulse {
        animation: walk-tile-pulse 1.7s ease-in-out 3;
        border-color: var(--forest);
      }
      @keyframes walk-tile-pulse {
        0%,
        100% {
          box-shadow: var(--shadow-sm);
          filter: brightness(1);
        }
        50% {
          box-shadow:
            0 0 0 3px rgba(47, 93, 50, 0.22),
            0 10px 28px rgba(47, 93, 50, 0.18);
          filter: brightness(1.04);
        }
      }
      .walk-info {
        flex: 1;
        min-width: 0;
      }
      .walk-info h3,
      .archived-info h3 {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        flex-wrap: wrap;
      }
      .walk-info h3 {
        margin: 0 0 0.35rem;
        font-size: 1.02rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .walk-info p {
        margin: 0 0 0.6rem;
        color: var(--muted);
        font-size: 0.88rem;
        line-height: 1.4;
      }
      .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
      }
      .tag {
        display: inline-block;
        background: var(--forest-soft);
        border-radius: var(--radius-pill);
        padding: 0.2rem 0.65rem;
        font-size: 0.75rem;
        color: var(--forest);
        font-weight: 600;
      }
      .walk-meta {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.35rem;
        min-width: 90px;
      }
      .price {
        font-size: 1.15rem;
        font-weight: 800;
        color: var(--forest);
        font-family: var(--font-display);
      }
      .walker {
        font-size: 0.78rem;
        color: var(--muted);
        text-align: right;
      }
      .btn-book {
        margin-top: 0.25rem;
        background: var(--cta);
        color: var(--on-cta);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.5rem 0.95rem;
        font-size: 0.85rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
        text-decoration: none;
        box-shadow: 0 4px 12px rgba(232, 135, 42, 0.25);
        transition: background 0.15s;
      }
      .btn-book:hover {
        background: var(--cta-hover);
      }
      .btn-edit {
        margin-top: 0.15rem;
        background: transparent;
        color: var(--forest);
        border: 1px solid var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.4rem 0.85rem;
        font-size: 0.8rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-edit:hover {
        background: var(--forest-soft);
      }
      .archived-block {
        margin-top: 1.35rem;
      }
      .archived-block-title {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-start;
        gap: 0.45rem 0.75rem;
        margin-bottom: 0.75rem;
      }
      .archived-block-title h2 {
        margin: 0;
        flex: 1 1 auto;
        font-size: 1.1rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .archived-block-title .muted {
        flex: 1 1 100%;
        margin: 0;
        font-size: 0.82rem;
      }
      .status-filters {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
        margin: 0 0 0.85rem;
      }
      .status-chip {
        border: 1px solid transparent;
        font: inherit;
        font-size: 0.78rem;
        font-weight: 600;
        padding: 0.28rem 0.55rem;
        border-radius: var(--radius-sm);
        cursor: pointer;
        transition:
          background 0.12s ease,
          color 0.12s ease,
          border-color 0.12s ease;
      }
      .status-pill {
        border: 1px solid transparent;
        font-weight: 600;
      }
      .status-chip[data-status='ALL'],
      .status-pill[data-status='ALL'] {
        background: #eef2ef;
        border-color: #c5d0c7;
        color: #2f4634;
      }
      .status-chip[data-status='PUBLISHED'],
      .status-pill[data-status='PUBLISHED'] {
        background: #e5f3ea;
        border-color: #8fbf9a;
        color: #1a6b36;
      }
      .status-chip[data-status='IN_PROGRESS'],
      .status-pill[data-status='IN_PROGRESS'] {
        background: #e7f0fb;
        border-color: #8ab0de;
        color: #1d4f91;
      }
      .status-chip[data-status='COMPLETED'],
      .status-pill[data-status='COMPLETED'] {
        background: #e8eaf8;
        border-color: #9aa3d6;
        color: #3a3f8f;
      }
      .status-chip[data-status='CANCELLED'],
      .status-pill[data-status='CANCELLED'] {
        background: #fbeaea;
        border-color: #e0a0a0;
        color: #9b2c2c;
      }
      .status-chip[data-status='DRAFT'],
      .status-pill[data-status='DRAFT'] {
        background: #f4f1ea;
        border-color: #d2c7b4;
        color: #6b5b3e;
      }
      .status-chip[data-status='ARCHIVED'],
      .status-pill[data-status='ARCHIVED'] {
        background: #f0ece6;
        border-color: #c4b8a8;
        color: #5c5346;
      }
      .status-chip.active[data-status='ALL'] {
        background: #2f4634;
        border-color: #2f4634;
        color: #fff;
      }
      .status-chip.active[data-status='PUBLISHED'] {
        background: #1a6b36;
        border-color: #1a6b36;
        color: #fff;
      }
      .status-chip.active[data-status='IN_PROGRESS'] {
        background: #1d4f91;
        border-color: #1d4f91;
        color: #fff;
      }
      .status-chip.active[data-status='COMPLETED'] {
        background: #3a3f8f;
        border-color: #3a3f8f;
        color: #fff;
      }
      .status-chip.active[data-status='CANCELLED'] {
        background: #9b2c2c;
        border-color: #9b2c2c;
        color: #fff;
      }
      .status-chip.active[data-status='DRAFT'] {
        background: #6b5b3e;
        border-color: #6b5b3e;
        color: #fff;
      }
      .status-chip.active[data-status='ARCHIVED'] {
        background: #5c5346;
        border-color: #5c5346;
        color: #fff;
      }
      .archived-tile {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .archived-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.9rem 1rem 0.65rem;
        border-bottom: 1px solid var(--line);
        background: linear-gradient(180deg, var(--bg) 0%, transparent 100%);
      }
      .archived-head h2 {
        margin: 0 0 0.15rem;
        font-size: 1.05rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .archived-head .muted {
        margin: 0;
        font-size: 0.82rem;
        color: var(--muted);
      }
      .archived-count {
        flex-shrink: 0;
        min-width: 1.6rem;
        height: 1.6rem;
        padding: 0 0.4rem;
        border-radius: 999px;
        background: var(--forest-soft, #dfeadf);
        color: var(--forest-deep);
        font-size: 0.78rem;
        font-weight: 800;
        display: inline-grid;
        place-items: center;
      }
      .archived-count.sm {
        min-width: 1.35rem;
        height: 1.35rem;
        font-size: 0.7rem;
      }
      .region-stack {
        display: flex;
        flex-direction: column;
        gap: 0.85rem;
      }
      .region-tile {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .region-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.65rem;
        padding: 0.75rem 0.9rem;
        border-bottom: 1px solid var(--line);
        background: linear-gradient(180deg, var(--bg) 0%, transparent 100%);
      }
      .region-tile:not(.is-open) .region-head {
        border-bottom: none;
      }
      .fold-head {
        width: 100%;
        border: none;
        background: inherit;
        text-align: left;
        cursor: pointer;
        font: inherit;
        color: inherit;
      }
      .fold-head .fold-chevron {
        flex-shrink: 0;
        width: 1rem;
        text-align: center;
        font-size: 1.1rem;
        line-height: 1;
        color: var(--muted);
        transform: rotate(0deg);
        transition: transform 0.15s ease;
      }
      .region-tile.is-open > .fold-head .fold-chevron,
      .dept-tile.is-open > .fold-head .fold-chevron {
        transform: rotate(90deg);
      }
      .fold-head h3,
      .fold-head h4 {
        flex: 1 1 auto;
      }
      .region-head h3 {
        margin: 0;
        font-size: 1rem;
        font-family: var(--font-display);
        color: var(--forest-deep);
      }
      .dept-stack {
        display: flex;
        flex-direction: column;
        gap: 0.55rem;
        padding: 0.65rem;
      }
      .dept-tile {
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
        overflow: hidden;
      }
      .dept-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0.55rem 0.7rem;
        border-bottom: 1px solid var(--line);
      }
      .dept-tile:not(.is-open) .dept-head {
        border-bottom: none;
      }
      .dept-head h4 {
        margin: 0;
        font-size: 0.88rem;
        color: var(--forest-deep);
      }
      .dept-tile .archived-list {
        background: var(--surface);
      }
      .archived-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
      }
      .archived-item {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.85rem 1rem;
        border-bottom: 1px solid var(--line);
      }
      .archived-item:last-child {
        border-bottom: 0;
      }
      .walk-thumb.sm {
        width: 3.25rem;
        height: 3.25rem;
      }
      .archived-info {
        flex: 1;
        min-width: 0;
      }
      .archived-info h3 {
        margin: 0 0 0.2rem;
        font-size: 0.95rem;
        color: var(--forest-deep);
      }
      .archived-info .meta {
        margin: 0 0 0.4rem;
        font-size: 0.78rem;
        color: var(--muted);
      }
      .archived-info .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 0.3rem;
      }
      .btn-book.ghost {
        background: transparent;
        color: var(--forest-deep);
        border: 1px solid var(--line);
        box-shadow: none;
      }
      .pad {
        padding: 0.85rem 1rem 1rem;
      }
    `,
  ],
})
export class CatalogComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(AuthService);
  protected readonly attendance = inject(AttendanceReminderService);
  private readonly citySelection = inject(CitySelectionService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly cdr = inject(ChangeDetectorRef);
  private pulseTimer?: ReturnType<typeof setTimeout>;

  protected readonly walks = signal<Walk[]>([]);
  protected readonly archived = signal<Walk[]>([]);
  protected readonly adminWalks = signal<Walk[]>([]);
  protected readonly myWalks = signal<Walk[]>([]);
  protected readonly loading = signal(false);
  protected readonly archivedLoading = signal(false);
  protected readonly archivedError = signal<string | null>(null);
  protected readonly adminLoading = signal(false);
  protected readonly adminError = signal<string | null>(null);
  protected readonly myUpcomingLoading = signal(false);
  protected readonly selectedCity = signal<City | null>(null);
  protected readonly modalCity = signal<City | null>(null);
  protected readonly pulsedWalkId = signal<number | null>(null);
  /** '' = tous ; sinon code statut ou ARCHIVED. */
  protected readonly adminStatusFilter = signal<string>('');
  /** Régions / départements ouverts (fermés par défaut). */
  protected readonly openRegions = signal<ReadonlySet<string>>(new Set());
  protected readonly openDepts = signal<ReadonlySet<string>>(new Set());

  protected readonly createOpen = signal(false);
  protected readonly editingWalk = signal<CommunityWalkEditModel | null>(null);
  protected readonly createHint = signal<string | null>(null);
  protected readonly rollcallOpen = signal<RosterResponse | null>(null);
  protected readonly rollcallPresent = signal<Record<number, boolean>>({});
  protected readonly rollcallLoading = signal(false);
  protected readonly rollcallSaving = signal(false);
  protected readonly rollcallError = signal<string | null>(null);

  protected readonly walkTypeLabel = walkTypeLabel;
  protected readonly walkStatusLabel = walkStatusLabel;

  protected readonly adminStatusOptions: ReadonlyArray<{ value: string; label: string }> = [
    { value: '', label: 'Toutes' },
    { value: 'PUBLISHED', label: 'Publiée' },
    { value: 'IN_PROGRESS', label: 'En cours' },
    { value: 'COMPLETED', label: 'Terminée' },
    { value: 'CANCELLED', label: 'Annulée' },
    { value: 'DRAFT', label: 'Brouillon' },
    { value: 'ARCHIVED', label: 'Archivée' },
  ];

  protected readonly canCreateCommunityWalk = computed(() => {
    const roles = this.auth.roles();
    return roles.includes('COMMUNITY_ORG') || roles.includes('ADMIN');
  });

  protected readonly canOrganizeWalks = computed(() => {
    const roles = this.auth.roles();
    return (
      roles.includes('COMMUNITY_ORG') ||
      roles.includes('ADMIN') ||
      roles.includes('WALKER_STAFF') ||
      roles.includes('WALKER_INDEP')
    );
  });

  /** Balades créées par l’utilisateur, encore à venir (hors archivées / terminées / annulées). */
  protected readonly myUpcomingWalks = computed(() => {
    const uid = this.auth.userId();
    if (uid == null) {
      return [];
    }
    const now = Date.now();
    return this.myWalks()
      .filter((w) => w.organizerUserId === uid)
      .filter((w) => !w.archivedAt)
      .filter((w) => {
        const status = (w.status || '').toUpperCase();
        if (status === 'COMPLETED' || status === 'CANCELLED') {
          return false;
        }
        if (status === 'IN_PROGRESS') {
          return true;
        }
        return new Date(w.startsAt).getTime() >= now;
      })
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  });

  /** Admin : balades groupées région → département (tri ville dans chaque dept). */
  protected readonly adminByRegion = computed(() => groupWalksByRegionDept(this.adminWalks()));

  protected readonly cityCenter = computed(() => {
    const city = this.selectedCity();
    if (
      city?.lat == null ||
      city.lng == null ||
      !Number.isFinite(city.lat) ||
      !Number.isFinite(city.lng)
    ) {
      return null;
    }
    return { lat: city.lat, lng: city.lng };
  });

  ngOnInit(): void {
    this.reloadMyUpcoming();
    this.attendance.refresh();
    const shared = this.citySelection.selectedCity();
    if (shared) {
      this.onCity(shared);
    } else if (this.auth.isAdmin()) {
      this.reloadAdminOverview(null);
    }
  }

  ngOnDestroy(): void {
    if (this.pulseTimer) {
      clearTimeout(this.pulseTimer);
    }
  }

  protected onCity(city: City): void {
    this.selectedCity.set(city);
    this.citySelection.setCity(city);
    this.createHint.set(null);
    this.closeCreateModal();
    this.openRegions.set(new Set());
    this.openDepts.set(new Set());
    this.reloadWalks(city.id);
    if (this.auth.isAdmin()) {
      this.reloadAdminOverview(city.id);
    } else {
      this.reloadArchived(city.id);
    }
  }

  protected openRollcall(item: PendingRollcallWalk): void {
    this.rollcallError.set(null);
    this.rollcallLoading.set(true);
    this.rollcallOpen.set({
      walkId: item.walkId,
      title: item.title,
      startsAt: item.startsAt,
      endsAt: item.endsAt,
      entries: [],
    });
    this.http.get<RosterResponse>(`/api/attendance/walks/${item.walkId}/roster`).subscribe({
      next: (roster) => {
        const present: Record<number, boolean> = {};
        for (const entry of roster.entries) {
          present[entry.bookingId] =
            entry.attendanceStatus === 'PRESENT' || entry.bookingStatus === 'COMPLETED';
        }
        this.rollcallPresent.set(present);
        this.rollcallOpen.set(roster);
        this.rollcallLoading.set(false);
      },
      error: () => {
        this.rollcallLoading.set(false);
        this.rollcallError.set('Impossible de charger la liste des inscrits.');
      },
    });
  }

  protected closeRollcall(): void {
    this.rollcallOpen.set(null);
    this.rollcallPresent.set({});
    this.rollcallError.set(null);
    this.rollcallSaving.set(false);
  }

  protected togglePresent(bookingId: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.rollcallPresent.update((map) => ({ ...map, [bookingId]: checked }));
  }

  protected formatParticipant(name: string): string {
    const trimmed = (name || '').trim();
    if (!trimmed) {
      return '—';
    }
    if (trimmed.startsWith('@') || /\s/.test(trimmed)) {
      return trimmed;
    }
    return `@${trimmed}`;
  }

  protected submitRollcall(): void {
    const roster = this.rollcallOpen();
    if (!roster || this.rollcallSaving()) {
      return;
    }
    const presentMap = this.rollcallPresent();
    const items = roster.entries.map((entry) => ({
      bookingId: entry.bookingId,
      status: presentMap[entry.bookingId] ? 'PRESENT' : 'ABSENT',
    }));
    this.rollcallSaving.set(true);
    this.rollcallError.set(null);
    this.http
      .post(`/api/attendance/walks/${roster.walkId}/rollcall`, { items })
      .subscribe({
        next: () => {
          this.rollcallSaving.set(false);
          this.closeRollcall();
          this.attendance.refresh();
          this.reloadMyUpcoming();
          const city = this.selectedCity();
          if (city) {
            this.reloadWalks(city.id);
          }
          if (this.auth.isAdmin()) {
            this.reloadAdminOverview(city?.id ?? null);
          }
        },
        error: (err) => {
          this.rollcallSaving.set(false);
          const msg =
            err?.error?.detail ||
            err?.error?.title ||
            err?.error?.message ||
            'Validation impossible.';
          this.rollcallError.set(typeof msg === 'string' ? msg : 'Validation impossible.');
        },
      });
  }

  protected setAdminStatusFilter(value: string): void {
    if (this.adminStatusFilter() === value) {
      return;
    }
    this.adminStatusFilter.set(value);
    this.openRegions.set(new Set());
    this.openDepts.set(new Set());
    this.reloadAdminOverview(this.selectedCity()?.id ?? null);
  }

  protected isRegionOpen(region: string): boolean {
    return this.openRegions().has(region);
  }

  protected isDeptOpen(region: string, dept: string): boolean {
    return this.openDepts().has(deptKey(region, dept));
  }

  protected toggleRegion(region: string): void {
    const next = new Set(this.openRegions());
    if (next.has(region)) {
      next.delete(region);
    } else {
      next.add(region);
    }
    this.openRegions.set(next);
  }

  protected toggleDept(region: string, dept: string): void {
    const key = deptKey(region, dept);
    const next = new Set(this.openDepts());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.openDepts.set(next);
  }

  /** Clé couleur pastille (= statut métier affiché). */
  protected statusPillKey(walk: Walk): string {
    const status = (walk.status || '').toUpperCase();
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

  protected onPinSelected(walkId: number): void {
    this.pulsedWalkId.set(walkId);
    this.cdr.detectChanges();
    if (this.pulseTimer) {
      clearTimeout(this.pulseTimer);
    }
    requestAnimationFrame(() => {
      const tile = this.host.nativeElement.querySelector(
        `[data-walk-id="${walkId}"]`,
      ) as HTMLElement | null;
      tile?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    this.pulseTimer = setTimeout(() => this.pulsedWalkId.set(null), 5200);
  }

  protected canEditWalk(walk: Walk): boolean {
    if (walk.status === 'CANCELLED' || walk.status === 'COMPLETED') {
      return false;
    }
    const uid = this.auth.userId();
    if (this.auth.isAdmin()) {
      return true;
    }
    return uid != null && walk.organizerUserId === uid;
  }

  protected canLaunchWalk(walk: Walk): boolean {
    if (walk.archivedAt || walk.status === 'COMPLETED' || walk.status === 'CANCELLED') {
      return false;
    }
    return this.canEditWalk(walk) || walk.status === 'IN_PROGRESS' || walk.status === 'PUBLISHED';
  }

  protected openCreateModal(): void {
    if (!this.canCreateCommunityWalk()) {
      return;
    }
    const city = this.selectedCity();
    if (!city) {
      this.createHint.set('Sélectionnez d’abord une ville pour proposer une balade.');
      return;
    }
    this.createHint.set(null);
    this.editingWalk.set(null);
    this.modalCity.set(city);
    this.createOpen.set(true);
  }

  protected openEditModal(walk: Walk): void {
    if (!this.canEditWalk(walk)) {
      return;
    }
    const selected = this.selectedCity();
    const city: City =
      selected?.id === walk.cityId
        ? selected
        : {
            id: walk.cityId,
            name: walk.cityName || 'Ville',
            postalCode: '',
            lat: walk.lat,
            lng: walk.lng,
          };
    this.createHint.set(null);
    this.modalCity.set(city);
    this.editingWalk.set({
      id: walk.id,
      cityId: walk.cityId,
      title: walk.title,
      description: walk.description,
      meetingPointId: walk.meetingPointId,
      lat: walk.lat,
      lng: walk.lng,
      startsAt: walk.startsAt,
      durationMinutes: walk.durationMinutes,
      elevationGainM: walk.elevationGainM ?? 0,
      terrainType: walk.terrainType ?? 'PARK',
      difficultyLevel: walk.difficultyLevel ?? null,
      capacity: walk.capacity,
      acceptedSizes: walk.acceptedSizes,
      acceptedEnergy: walk.acceptedEnergy,
      minSociabilityDogs: walk.minSociabilityDogs,
      routeHint: walk.routeHint,
      routeGeojson: walk.routeGeojson,
    });
    this.createOpen.set(true);
  }

  protected closeCreateModal(): void {
    this.createOpen.set(false);
    this.editingWalk.set(null);
    this.modalCity.set(null);
  }

  protected onWalkModalSaved(): void {
    const city = this.selectedCity();
    this.closeCreateModal();
    this.reloadMyUpcoming();
    if (city) {
      this.reloadWalks(city.id);
      if (this.auth.isAdmin()) {
        this.reloadAdminOverview(city.id);
      } else {
        this.reloadArchived(city.id);
      }
    } else if (this.auth.isAdmin()) {
      this.reloadAdminOverview(null);
    }
  }

  private reloadMyUpcoming(): void {
    if (!this.auth.loggedIn()) {
      this.myWalks.set([]);
      this.myUpcomingLoading.set(false);
      return;
    }
    this.myUpcomingLoading.set(true);
    this.http.get<Walk[]>('/api/walks/mine').subscribe({
      next: (walks) => {
        this.myWalks.set(walks);
        this.myUpcomingLoading.set(false);
      },
      error: () => {
        this.myWalks.set([]);
        this.myUpcomingLoading.set(false);
      },
    });
  }

  private reloadWalks(cityId: number): void {
    this.loading.set(true);
    this.http.get<Walk[]>('/api/walks', { params: { cityId: cityId.toString() } }).subscribe({
      next: (walks) => {
        this.walks.set(walks);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private reloadAdminOverview(cityId: number | null): void {
    this.adminError.set(null);
    if (!this.auth.isAdmin()) {
      this.adminWalks.set([]);
      this.adminLoading.set(false);
      return;
    }
    this.adminLoading.set(true);
    const params: Record<string, string> = {};
    if (cityId != null) {
      params['cityId'] = cityId.toString();
    }
    const status = this.adminStatusFilter();
    if (status) {
      params['status'] = status;
    }
    this.http
      .get<Walk[]>('/api/walks/admin-overview', Object.keys(params).length ? { params } : {})
      .subscribe({
        next: (walks) => {
          this.adminWalks.set(walks);
          this.adminLoading.set(false);
        },
        error: (err) => {
          this.adminWalks.set([]);
          this.adminLoading.set(false);
          this.adminError.set(
            err?.status === 401
              ? 'Session expirée — reconnectez-vous.'
              : err?.status === 403
                ? 'Réservé aux administrateurs.'
                : 'Impossible de charger les balades.',
          );
        },
      });
  }

  private reloadArchived(cityId: number | null): void {
    this.archivedError.set(null);
    if (!this.auth.loggedIn()) {
      this.archived.set([]);
      this.archivedLoading.set(false);
      return;
    }
    this.archivedLoading.set(true);
    const params = cityId == null ? undefined : { cityId: cityId.toString() };
    this.http.get<Walk[]>('/api/walks/archived', params ? { params } : {}).subscribe({
      next: (walks) => {
        this.archived.set(walks);
        this.archivedLoading.set(false);
      },
      error: (err) => {
        this.archived.set([]);
        this.archivedLoading.set(false);
        this.archivedError.set(
          err?.status === 401
            ? 'Session expirée — reconnectez-vous pour voir les archives.'
            : 'Impossible de charger les balades archivées.',
        );
      },
    });
  }
}

function deptKey(region: string, dept: string): string {
  return `${region}::${dept}`;
}

function groupWalksByRegionDept(walks: Walk[]): Array<{
  name: string;
  count: number;
  departments: Array<{ code: string; walks: Walk[] }>;
}> {
  const groups = new Map<string, Map<string, Walk[]>>();
  for (const walk of walks) {
    const region = (walk.region || 'Autre région').trim() || 'Autre région';
    const dept = (walk.department || '—').trim() || '—';
    if (!groups.has(region)) {
      groups.set(region, new Map());
    }
    const depts = groups.get(region)!;
    if (!depts.has(dept)) {
      depts.set(dept, []);
    }
    depts.get(dept)!.push(walk);
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b, 'fr'))
    .map(([name, depts]) => {
      const departments = [...depts.entries()]
        .sort(([a], [b]) => a.localeCompare(b, 'fr', { numeric: true }))
        .map(([code, list]) => ({
          code,
          walks: [...list].sort((x, y) => {
            const cityCmp = (x.cityName || '').localeCompare(y.cityName || '', 'fr');
            if (cityCmp !== 0) {
              return cityCmp;
            }
            return y.startsAt.localeCompare(x.startsAt);
          }),
        }));
      return {
        name,
        count: departments.reduce((n, d) => n + d.walks.length, 0),
        departments,
      };
    });
}
