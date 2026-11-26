// Consultation et édition du profil — champs sensibles → validation admin.
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { Router, RouterLink } from '@angular/router';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../../shared/photo-uploader/photo-uploader.component';
import { CityPickerComponent } from '../../shared/city-picker/city-picker.component';
import { City } from '../../shared/city.service';
import {
  CommunityWalkEditModel,
  CommunityWalkModalComponent,
} from '../../shared/community-walk-modal/community-walk-modal.component';
import { RouteGeoJson } from '../../shared/route-editor/route-editor.component';
import {
  roleLabel,
  walkStatusLabel,
  walkTypeLabel,
  reviewStatusLabel,
} from '../../shared/labels';
import { WalkDifficultyBarComponent } from '../../shared/walk-difficulty-bar/walk-difficulty-bar.component';

interface OrganizedWalk {
  id: number;
  title: string;
  description?: string | null;
  cityId: number;
  cityName?: string | null;
  meetingPointId: number;
  lat?: number | null;
  lng?: number | null;
  walkType: string;
  startsAt: string;
  durationMinutes: number;
  elevationGainM?: number;
  terrainType?: string;
  difficultyLevel?: number;
  capacity: number;
  bookedCount: number;
  status: string;
  organizerUserId?: number | null;
  organizerDisplayName?: string | null;
  archivedAt?: string | null;
  recycledFromWalkId?: number | null;
  promoPhotoUrls?: string[] | null;
  acceptedSizes?: string[];
  acceptedEnergy?: string[];
  minSociabilityDogs?: string | null;
  routeHint?: string | null;
  routeGeojson?: RouteGeoJson | string | null;
}

interface UserProfile {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  pseudo?: string | null;
  phone?: string | null;
  bio?: string | null;
  cityId?: number | null;
  zoneId?: number | null;
  completedWalksCount: number;
  averageRating?: number | null;
  ratingsCount?: number;
  communityOrgStatus: string;
  reviewStatus?: string;
  reviewRefusalReason?: string | null;
  roles: string[];
  photos?: PhotoItem[];
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    PhotoUploaderComponent,
    CityPickerComponent,
    CommunityWalkModalComponent,
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    WalkDifficultyBarComponent,
  ],
  template: `
    <div class="profile-page">
      <header class="page-header">
        <h1>Mon profil</h1>
        <p class="subtitle">
          Bio et photos libres. Nom, téléphone et ville nécessitent une validation admin.
        </p>
      </header>

      @if (info()) {
        <p class="banner info" role="status">{{ info() }}</p>
      }
      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="muted">Chargement…</p>
      } @else if (profile(); as p) {
        <div class="profile-card">
          @if (p.photos?.length) {
            <img class="avatar-img" [src]="p.photos![0].url" [alt]="p.firstName" />
          } @else {
            <div class="avatar">{{ initials() }}</div>
          }
          <div class="profile-info">
            <h2>{{ p.firstName }} {{ p.lastName }}</h2>
            @if (p.pseudo) {
              <p class="pseudo">@{{ p.pseudo }}</p>
            }
            <p class="email">{{ p.email }}</p>
            <div class="roles">
              @for (role of p.roles ?? []; track role) {
                <span class="role-badge">{{ roleLabel(role) }}</span>
              }
            </div>
            <p class="muted">Balades terminées : {{ p.completedWalksCount }}</p>
            @if ((p.ratingsCount ?? 0) > 0 && p.averageRating != null) {
              <p class="muted rating-line">
                Note organisateur : {{ p.averageRating | number: '1.1-2' }}/5
                ({{ p.ratingsCount }} avis)
              </p>
            } @else {
              <p class="muted rating-line">Note organisateur : pas encore d’avis</p>
            }
            <p class="status" [attr.data-status]="p.reviewStatus || 'VALIDATED'">
              {{ reviewLabel(p.reviewStatus) }}
            </p>
            @if (p.reviewStatus === 'PENDING') {
              <p class="review-hint">Modifications sensibles en attente d’approbation admin</p>
            }
            @if (p.reviewStatus === 'REFUSED' && p.reviewRefusalReason) {
              <p class="review-hint refuse">{{ p.reviewRefusalReason }}</p>
            }
          </div>
        </div>

        @if (isAdmin()) {
          <a routerLink="/admin" class="admin-link">Administration</a>
        }

        <section class="panel walks-panel">
          <div class="panel-head">
            <h2>Mes balades</h2>
            <a routerLink="/catalog" class="btn-ghost linkish">Catalogue</a>
          </div>
          @if (walksLoading()) {
            <p class="muted">Chargement des balades…</p>
          } @else if (myWalks().length === 0) {
            <p class="muted empty-walks">
              Aucune balade organisée pour le moment.
              <a routerLink="/catalog">En proposer une</a>
            </p>
          } @else {
            <ul class="walk-tiles">
              @for (walk of myWalks(); track walk.id) {
                <li class="walk-tile">
                  <div class="walk-tile-main">
                    <h3>{{ walk.title }}</h3>
                    <p class="walk-meta">
                      {{ walk.cityName || 'Ville' }} · {{ walk.startsAt | date: 'short' }}
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
                      <span class="tag">{{ walk.bookedCount }}/{{ walk.capacity }}</span>
                      <span class="tag status-tag" [attr.data-status]="walk.status">{{
                        walkStatusLabel(walk.status)
                      }}</span>
                      @if (walk.archivedAt) {
                        <span class="tag status-tag" data-status="ARCHIVED">Archivée</span>
                      }
                    </div>
                  </div>
                  <div class="tile-actions">
                    @if (canLaunchWalk(walk)) {
                      <a class="btn-launch" [routerLink]="['/walks', walk.id, 'live']">
                        {{ walk.status === 'IN_PROGRESS' ? 'Reprendre' : 'Lancer' }}
                      </a>
                    }
                    @if (canEditWalk(walk)) {
                      <button type="button" class="btn-edit" (click)="openEditWalk(walk)">
                        Modifier
                      </button>
                    }
                    @if (canArchiveWalk(walk)) {
                      <button
                        type="button"
                        class="btn-archive"
                        [disabled]="actingWalkId() === walk.id"
                        (click)="archiveWalk(walk)"
                      >
                        Archiver
                      </button>
                    }
                    @if (canRecycleWalk(walk)) {
                      <button
                        type="button"
                        class="btn-edit"
                        [disabled]="actingWalkId() === walk.id"
                        (click)="recycleWalk(walk)"
                      >
                        Recycler
                      </button>
                    }
                    <a class="btn-open" routerLink="/catalog">Voir</a>
                  </div>
                </li>
              }
            </ul>
          }
        </section>

        @if (editModalCity(); as city) {
          <app-community-walk-modal
            [city]="city"
            [walk]="editModalWalk()"
            (closed)="closeEditWalk()"
            (saved)="onWalkSaved()"
          />
        }

        <section class="panel">
          <div class="panel-head">
            <h2>Modifier ma fiche</h2>
            @if (!editing()) {
              <button type="button" class="btn-ghost" (click)="startEdit()">Éditer</button>
            }
          </div>

          @if (editing()) {
            <form [formGroup]="form" (ngSubmit)="save()" class="form">
              <div class="form-row">
                <label>
                  Prénom
                  <input formControlName="firstName" type="text" autocomplete="given-name" />
                </label>
                <label>
                  Nom
                  <input formControlName="lastName" type="text" autocomplete="family-name" />
                </label>
              </div>
              <label>
                Pseudo
                <input
                  formControlName="pseudo"
                  type="text"
                  maxlength="40"
                  autocomplete="nickname"
                  placeholder="Affiché sur les balades"
                />
              </label>
              <label>
                Téléphone
                <input formControlName="phone" type="tel" autocomplete="tel" />
              </label>
              <label>
                Bio
                <textarea formControlName="bio" rows="3" maxlength="500"></textarea>
              </label>
              <label>
                Ville
                <app-city-picker
                  [initialCity]="selectedCity()"
                  (citySelected)="onCity($event)"
                />
              </label>
              <p class="field-hint">
                Sensibles : prénom, nom, téléphone, ville. Libre : pseudo, bio, photos.
              </p>
              <div class="form-actions">
                <button type="submit" [disabled]="form.invalid || saving()">Enregistrer</button>
                <button type="button" class="btn-ghost" (click)="cancelEdit()">Annuler</button>
              </div>
            </form>
          } @else {
            <dl class="facts">
              <div>
                <dt>Pseudo</dt>
                <dd>{{ p.pseudo ? '@' + p.pseudo : '—' }}</dd>
              </div>
              <div>
                <dt>Téléphone</dt>
                <dd>{{ p.phone || '—' }}</dd>
              </div>
              <div>
                <dt>Bio</dt>
                <dd>{{ p.bio || '—' }}</dd>
              </div>
            </dl>
          }
        </section>

        <section class="panel">
          <app-photo-uploader
            title="Mes photos"
            uploadUrl="/api/me/photos"
            [initialPhotos]="p.photos ?? []"
            (photosChange)="onPhotosChange($event)"
          />
        </section>

        <button type="button" class="logout" (click)="logout()">Déconnexion</button>
      }
    </div>
  `,
  styles: [
    `
      .profile-page {
        max-width: 28rem;
        margin: 0 auto;
        padding: 1.1rem 1rem 1.5rem;
      }
      h1 {
        font-family: var(--font-display);
        margin: 0 0 0.25rem;
        color: var(--forest-deep);
      }
      .subtitle {
        color: var(--muted);
        margin: 0 0 1.1rem;
        font-size: 0.92rem;
      }
      .profile-card {
        display: flex;
        gap: 1.25rem;
        align-items: center;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 1.25rem;
        box-shadow: var(--shadow-sm);
        margin-bottom: 0.85rem;
      }
      .panel {
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-md);
        padding: 1rem 1.1rem;
        box-shadow: var(--shadow-sm);
        margin-bottom: 0.85rem;
      }
      .panel-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 0.75rem;
      }
      .panel-head h2 {
        margin: 0;
        font-size: 1.02rem;
        color: var(--forest-deep);
      }
      .linkish {
        text-decoration: none;
        display: inline-flex;
        align-items: center;
        padding: 0.35rem 0.75rem;
        font-size: 0.82rem;
      }
      .empty-walks a {
        color: var(--forest);
        font-weight: 600;
      }
      .walk-tiles {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }
      .walk-tile {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.75rem 0.85rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        background: var(--bg);
      }
      .walk-tile-main {
        min-width: 0;
        flex: 1;
      }
      .walk-tile h3 {
        margin: 0 0 0.2rem;
        font-size: 0.98rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .walk-meta {
        margin: 0 0 0.45rem;
        font-size: 0.8rem;
        color: var(--muted);
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
        font-weight: 600;
      }
      .status-tag[data-status='PUBLISHED'] {
        background: #e8f2e8;
      }
      .status-tag[data-status='IN_PROGRESS'] {
        background: #dff3e4;
        color: var(--forest);
      }
      .status-tag[data-status='CANCELLED'] {
        background: #fde8e8;
        color: var(--danger);
      }
      .status-tag[data-status='COMPLETED'] {
        background: #e8eef2;
        color: var(--muted);
      }
      .status-tag[data-status='ARCHIVED'] {
        background: #eee8df;
        color: #6b5e4e;
      }
      .rating-line {
        margin: 0.15rem 0 0;
        font-size: 0.88rem;
      }
      .tile-actions {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.35rem;
        flex-shrink: 0;
      }
      .btn-launch {
        background: var(--forest);
        color: var(--on-forest);
        border-radius: var(--radius-pill);
        padding: 0.4rem 0.8rem;
        font-size: 0.8rem;
        font-weight: 700;
        font-family: var(--font-display);
        text-decoration: none;
      }
      .btn-edit {
        background: transparent;
        color: var(--forest);
        border: 1px solid var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.35rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-archive {
        background: transparent;
        color: #6b5e4e;
        border: 1px solid #c9bba8;
        border-radius: var(--radius-pill);
        padding: 0.35rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-archive:disabled {
        opacity: 0.55;
        cursor: wait;
      }
      .btn-open {
        background: transparent;
        color: var(--muted);
        border: 1px solid var(--line);
        border-radius: var(--radius-pill);
        padding: 0.35rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 700;
        font-family: var(--font-display);
        text-decoration: none;
      }
      .avatar,
      .avatar-img {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        flex-shrink: 0;
      }
      .avatar {
        background: var(--forest);
        color: var(--on-forest);
        display: grid;
        place-items: center;
        font-weight: 800;
        font-family: var(--font-display);
        font-size: 1.2rem;
      }
      .avatar-img {
        object-fit: cover;
        border: 2px solid var(--forest-soft);
      }
      .email {
        color: var(--muted);
        margin: 0.25rem 0 0.5rem;
      }
      .pseudo {
        margin: 0.15rem 0 0;
        color: var(--forest);
        font-weight: 700;
        font-size: 0.92rem;
      }
      .roles {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
        margin-bottom: 0.5rem;
      }
      .role-badge {
        background: var(--forest-soft);
        color: var(--forest);
        border-radius: var(--radius-pill);
        padding: 0.2rem 0.6rem;
        font-size: 0.75rem;
        font-weight: 600;
      }
      .status {
        margin: 0.35rem 0 0;
        font-size: 0.8rem;
        font-weight: 700;
      }
      .status[data-status='VALIDATED'] {
        color: var(--forest);
      }
      .status[data-status='PENDING'] {
        color: var(--warn, #b7791f);
      }
      .status[data-status='REFUSED'] {
        color: var(--danger);
      }
      .review-hint {
        margin: 0.25rem 0 0;
        font-size: 0.78rem;
        color: var(--warn, #b7791f);
      }
      .review-hint.refuse {
        color: var(--danger);
      }
      .muted {
        color: var(--muted);
      }
      .form-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.75rem;
      }
      label {
        display: grid;
        gap: 0.35rem;
        margin-bottom: 0.75rem;
        font-size: 0.9rem;
        font-weight: 600;
      }
      input,
      textarea {
        padding: 0.55rem 0.7rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        font: inherit;
        background: var(--bg);
        width: 100%;
        box-sizing: border-box;
      }
      .field-hint {
        font-size: 0.72rem;
        color: var(--muted);
        margin: 0 0 0.85rem;
        line-height: 1.35;
      }
      .form-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.55rem;
      }
      button {
        border: 0;
        border-radius: var(--radius-pill);
        padding: 0.7rem 1.1rem;
        background: var(--cta);
        color: var(--on-cta);
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-ghost {
        background: transparent;
        color: var(--forest);
        border: 1px solid var(--forest-soft);
      }
      .facts {
        margin: 0;
        display: grid;
        gap: 0.65rem;
      }
      .facts div {
        display: grid;
        gap: 0.15rem;
      }
      .facts dt {
        font-size: 0.75rem;
        font-weight: 700;
        color: var(--muted);
        text-transform: uppercase;
        letter-spacing: 0.03em;
      }
      .facts dd {
        margin: 0;
        font-size: 0.95rem;
        color: var(--forest-deep);
        white-space: pre-wrap;
      }
      .admin-link {
        display: block;
        text-align: center;
        text-decoration: none;
        margin-bottom: 0.85rem;
        padding: 0.75rem 1rem;
        border-radius: var(--radius-md);
        border: 1px solid var(--line);
        background: var(--surface);
        color: var(--forest-deep);
        font-weight: 700;
        font-family: var(--font-display);
        box-shadow: var(--shadow-sm);
      }
      .logout {
        margin-top: 0.4rem;
        width: 100%;
        min-height: 2.75rem;
        border-radius: var(--radius-pill);
        border: 1.5px solid #e8b4b0;
        background: transparent;
        color: var(--danger);
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
        padding: 0.75rem 1rem;
        border-radius: var(--radius-sm);
        margin-bottom: 0.85rem;
      }
      .banner.info {
        background: #e8f2e8;
        color: var(--forest-deep);
        padding: 0.75rem 1rem;
        border-radius: var(--radius-sm);
        margin-bottom: 0.85rem;
      }
      @media (max-width: 480px) {
        .form-row {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ProfileComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly loading = signal(false);
  protected readonly walksLoading = signal(false);
  protected readonly myWalks = signal<OrganizedWalk[]>([]);
  protected readonly saving = signal(false);
  protected readonly editing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly selectedCity = signal<City | null>(null);
  protected readonly editModalCity = signal<City | null>(null);
  protected readonly editModalWalk = signal<CommunityWalkEditModel | null>(null);
  protected readonly actingWalkId = signal<number | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    pseudo: [''],
    phone: [''],
    bio: [''],
    cityId: [null as number | null],
  });

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected readonly roleLabel = roleLabel;
  protected readonly walkTypeLabel = walkTypeLabel;
  protected readonly walkStatusLabel = walkStatusLabel;

  protected readonly initials = () => {
    const p = this.profile();
    if (!p) return '?';
    return `${p.firstName?.[0] ?? ''}${p.lastName?.[0] ?? ''}`.toUpperCase();
  };

  protected reviewLabel(status?: string): string {
    return reviewStatusLabel(status);
  }

  protected onPhotosChange(photos: PhotoItem[]): void {
    const current = this.profile();
    if (!current) {
      return;
    }
    this.profile.set({ ...current, photos });
  }

  protected onCity(city: City): void {
    this.selectedCity.set(city);
    this.form.patchValue({ cityId: city.id });
  }

  protected startEdit(): void {
    const p = this.profile();
    if (!p) {
      return;
    }
    this.editing.set(true);
    this.info.set(null);
    this.error.set(null);
    this.form.reset({
      firstName: p.firstName,
      lastName: p.lastName,
      pseudo: p.pseudo ?? '',
      phone: p.phone ?? '',
      bio: p.bio ?? '',
      cityId: p.cityId ?? null,
    });
    this.selectedCity.set(
      p.cityId != null ? { id: p.cityId, name: `Ville #${p.cityId}`, postalCode: '' } : null,
    );
  }

  protected cancelEdit(): void {
    this.editing.set(false);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const current = this.profile();
    this.saving.set(true);
    this.error.set(null);
    this.info.set(null);
    this.http
      .put<UserProfile>('/api/me/profile', {
        firstName: v.firstName,
        lastName: v.lastName,
        pseudo: v.pseudo?.trim() || null,
        phone: v.phone || null,
        bio: v.bio || null,
        cityId: v.cityId,
        zoneId: current?.zoneId ?? null,
      })
      .subscribe({
        next: (p) => {
          this.saving.set(false);
          this.profile.set(p);
          this.editing.set(false);
          if (p.reviewStatus === 'PENDING') {
            this.info.set(
              'Modifications enregistrées. Une validation admin est requise (champs sensibles).',
            );
          } else {
            this.info.set('Profil mis à jour.');
          }
        },
        error: () => {
          this.saving.set(false);
          this.error.set('Modification impossible.');
        },
      });
  }

  protected canEditWalk(walk: OrganizedWalk): boolean {
    if (walk.archivedAt) {
      return false;
    }
    if (
      walk.status === 'CANCELLED' ||
      walk.status === 'COMPLETED' ||
      walk.status === 'IN_PROGRESS'
    ) {
      return false;
    }
    if (this.auth.isAdmin()) {
      return true;
    }
    const uid = this.auth.userId();
    return uid != null && walk.organizerUserId === uid;
  }

  protected canLaunchWalk(walk: OrganizedWalk): boolean {
    if (walk.archivedAt) {
      return false;
    }
    if (walk.status !== 'PUBLISHED' && walk.status !== 'IN_PROGRESS') {
      return false;
    }
    if (this.auth.isAdmin()) {
      return true;
    }
    const uid = this.auth.userId();
    return uid != null && walk.organizerUserId === uid;
  }

  protected canArchiveWalk(walk: OrganizedWalk): boolean {
    if (walk.archivedAt) {
      return false;
    }
    if (this.auth.isAdmin()) {
      return true;
    }
    const uid = this.auth.userId();
    return uid != null && walk.organizerUserId === uid;
  }

  protected archiveWalk(walk: OrganizedWalk): void {
    if (!this.canArchiveWalk(walk) || this.actingWalkId() != null) {
      return;
    }
    if (!confirm(`Archiver « ${walk.title} » ? Elle quittera le catalogue (pas de suppression).`)) {
      return;
    }
    this.actingWalkId.set(walk.id);
    this.error.set(null);
    this.http.post<OrganizedWalk>(`/api/walks/${walk.id}/archive`, {}).subscribe({
      next: () => {
        this.actingWalkId.set(null);
        this.info.set('Balade archivée.');
        this.loadMyWalks();
      },
      error: (err) => {
        this.actingWalkId.set(null);
        this.error.set(err?.error?.message || 'Archivage impossible.');
      },
    });
  }

  protected canRecycleWalk(walk: OrganizedWalk): boolean {
    if (!(walk.archivedAt || walk.status === 'COMPLETED' || walk.status === 'CANCELLED')) {
      return false;
    }
    if (this.auth.isAdmin()) {
      return true;
    }
    const uid = this.auth.userId();
    return uid != null && walk.organizerUserId === uid;
  }

  protected recycleWalk(walk: OrganizedWalk): void {
    if (!this.canRecycleWalk(walk) || this.actingWalkId() != null) {
      return;
    }
    const raw = prompt(
      `Nouvelle date de départ pour « ${walk.title} » (AAAA-MM-JJTHH:MM)`,
      new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString().slice(0, 16),
    );
    if (!raw) {
      return;
    }
    const startsAt = new Date(raw);
    if (Number.isNaN(startsAt.getTime()) || startsAt.getTime() <= Date.now()) {
      this.error.set('Indiquez une date future valide.');
      return;
    }
    this.actingWalkId.set(walk.id);
    this.error.set(null);
    this.http
      .post<OrganizedWalk>(`/api/walks/${walk.id}/recycle`, { startsAt: startsAt.toISOString() })
      .subscribe({
        next: (created) => {
          this.actingWalkId.set(null);
          this.info.set(`Balade recyclée (#${created.id}). Les photos et avis passés restent consultables.`);
          this.loadMyWalks();
        },
        error: (err) => {
          this.actingWalkId.set(null);
          this.error.set(err?.error?.message || 'Recyclage impossible.');
        },
      });
  }

  protected openEditWalk(walk: OrganizedWalk): void {
    if (!this.canEditWalk(walk) || walk.cityId == null) {
      return;
    }
    this.editModalCity.set({
      id: walk.cityId,
      name: walk.cityName || `Ville #${walk.cityId}`,
      postalCode: '',
      lat: walk.lat ?? null,
      lng: walk.lng ?? null,
    });
    this.editModalWalk.set({
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
  }

  protected closeEditWalk(): void {
    this.editModalCity.set(null);
    this.editModalWalk.set(null);
  }

  protected onWalkSaved(): void {
    this.closeEditWalk();
    this.loadMyWalks();
    this.info.set('Balade mise à jour.');
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  ngOnInit(): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.loading.set(true);
    this.http.get<UserProfile>('/api/me/profile').subscribe({
      next: (p) => {
        this.profile.set(p);
        this.loading.set(false);
        this.loadMyWalks();
      },
      error: () => {
        this.error.set('Impossible de charger le profil.');
        this.loading.set(false);
      },
    });
  }

  private loadMyWalks(): void {
    this.walksLoading.set(true);
    this.http.get<OrganizedWalk[]>('/api/walks/mine').subscribe({
      next: (walks) => {
        this.myWalks.set(walks);
        this.walksLoading.set(false);
      },
      error: () => {
        this.myWalks.set([]);
        this.walksLoading.set(false);
      },
    });
  }
}
