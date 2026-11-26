// Modale création / édition de balade communautaire.
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { City } from '../city.service';
import { MeetingPointMapPickerComponent } from '../meeting-point-map-picker/meeting-point-map-picker.component';
import {
  RouteEditorComponent,
  RouteGeoJson,
  estimateWalkMinutes,
  lineLengthMeters,
} from '../route-editor/route-editor.component';
import {
  TERRAIN_OPTIONS,
  difficultyLabel,
  estimateDifficultyLevel,
} from '../walk-difficulty';
import { WalkDifficultyBarComponent } from '../walk-difficulty-bar/walk-difficulty-bar.component';
import {
  PhotoItem,
  PhotoUploaderComponent,
} from '../photo-uploader/photo-uploader.component';

export interface CommunityWalkEditModel {
  id: number;
  cityId: number;
  title: string;
  description?: string | null;
  meetingPointId: number;
  lat?: number | null;
  lng?: number | null;
  startsAt: string;
  durationMinutes: number;
  elevationGainM?: number | null;
  terrainType?: string | null;
  difficultyLevel?: number | null;
  capacity: number;
  acceptedSizes?: string[];
  acceptedEnergy?: string[];
  minSociabilityDogs?: string | null;
  routeHint?: string | null;
  routeGeojson?: RouteGeoJson | string | null;
}

interface MeetingPointOption {
  id: number;
  cityId: number;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

const SIZE_OPTIONS = [
  { value: 'SMALL', label: 'Petit' },
  { value: 'MEDIUM', label: 'Moyen' },
  { value: 'LARGE', label: 'Grand' },
] as const;

const ENERGY_OPTIONS = [
  { value: 'LOW', label: 'Basse' },
  { value: 'MEDIUM', label: 'Moyenne' },
  { value: 'HIGH', label: 'Haute' },
] as const;

@Component({
  selector: 'app-community-walk-modal',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MeetingPointMapPickerComponent,
    RouteEditorComponent,
    WalkDifficultyBarComponent,
    PhotoUploaderComponent,
  ],
  template: `
    <div class="modal-backdrop" (click)="onClose()" role="presentation"></div>
    <div
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-walk-title"
      (click)="$event.stopPropagation()"
    >
      <header class="modal-head">
        <h2 id="community-walk-title">
          {{ walk ? 'Modifier la balade' : 'Proposer une balade' }}
        </h2>
        <button type="button" class="modal-close" (click)="onClose()" aria-label="Fermer">×</button>
      </header>
      <p class="modal-sub">
        Balade communautaire à {{ city.name }}. Gratuit pour les participants.
      </p>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }

      <form class="create-form" [formGroup]="form" (ngSubmit)="submit()">
        <label>
          Titre
          <input formControlName="title" type="text" maxlength="120" autocomplete="off" />
        </label>

        <label>
          Description
          <textarea formControlName="description" rows="3" maxlength="800"></textarea>
        </label>

        <div class="route-block">
          <span class="route-label">Point de rendez-vous</span>
          @if (meetingPointsLoading()) {
            <p class="field-hint">Chargement de la carte…</p>
          }
          <app-meeting-point-map-picker
            [center]="cityCenter"
            [meetingPoints]="meetingPoints()"
            [selectedId]="selectedMeetingPointId()"
            [placing]="placingMeetingPoint()"
            (meetingPointSelected)="onMeetingPointPicked($event)"
            (mapPlace)="onMapPlaceMeetingPoint($event)"
          />
        </div>

        <div class="route-block">
          <span class="route-label">Parcours (optionnel)</span>
          <app-route-editor
            [center]="routeEditorCenter()"
            [initialRoute]="draftRoute()"
            (routeChange)="onDraftRoute($event)"
          />
        </div>

        <div class="form-row">
          <label>
            Départ
            <input formControlName="startsAt" type="datetime-local" />
          </label>
          <label>
            Durée (min)
            <input formControlName="durationMinutes" type="number" min="15" step="5" />
            @if (durationFromRoute()) {
              <span class="field-hint">Estimée depuis le parcours (modifiable)</span>
            }
          </label>
        </div>

        <div class="form-row">
          <label>
            Dénivelé + (m)
            <input formControlName="elevationGainM" type="number" min="0" max="3000" step="10" />
          </label>
          <label>
            Type de terrain
            <select formControlName="terrainType">
              @for (opt of terrainOptions; track opt.value) {
                <option [value]="opt.value">{{ opt.label }}</option>
              }
            </select>
          </label>
        </div>

        <p class="difficulty-preview" role="status">
          Difficulté estimée :
          <app-walk-difficulty-bar [level]="previewDifficulty()" />
          · {{ difficultyHint(previewDifficulty()) }}
          <span class="field-hint">Calculée à partir de la durée, du dénivelé et du terrain</span>
        </p>

        <label>
          Capacité (chiens)
          <input formControlName="capacity" type="number" min="1" max="8" />
        </label>

        <fieldset>
          <legend>Tailles acceptées</legend>
          <div class="chip-row">
            @for (opt of sizeOptions; track opt.value) {
              <label class="chip">
                <input
                  type="checkbox"
                  [checked]="hasSize(opt.value)"
                  (change)="toggleSize(opt.value)"
                />
                {{ opt.label }}
              </label>
            }
          </div>
        </fieldset>

        <fieldset>
          <legend>Énergie acceptée</legend>
          <div class="chip-row">
            @for (opt of energyOptions; track opt.value) {
              <label class="chip">
                <input
                  type="checkbox"
                  [checked]="hasEnergy(opt.value)"
                  (change)="toggleEnergy(opt.value)"
                />
                {{ opt.label }}
              </label>
            }
          </div>
        </fieldset>

        <label>
          Sociabilité min. chiens
          <select formControlName="minSociabilityDogs">
            <option value="GOOD">Bonne</option>
            <option value="AVERAGE">Moyenne</option>
            <option value="FRAGILE">Fragile</option>
            <option value="UNKNOWN">Inconnue</option>
          </select>
        </label>

        <label>
          Indication d’itinéraire (optionnel)
          <input formControlName="routeHint" type="text" maxlength="200" />
        </label>

        @if (photoWalkId(); as wid) {
          <div class="promo-photos">
            <app-photo-uploader
              title="Photos d’accroche"
              [uploadUrl]="'/api/walks/' + wid + '/photos?kind=PROMO'"
              [deleteUrlBase]="'/api/walks/' + wid + '/photos'"
              [max]="6"
              [initialPhotos]="promoPhotos()"
              (photosChange)="promoPhotos.set($event)"
            />
            <p class="field-hint">Ces images motivent les participants sur les tuiles du catalogue.</p>
          </div>
        }

        <div class="modal-actions">
          <button type="button" class="btn-ghost" (click)="onClose()" [disabled]="saving()">
            {{ photoWalkId() && !walk ? 'Terminer' : 'Annuler' }}
          </button>
          @if (!photoWalkId() || walk) {
            <button type="submit" class="btn-create" [disabled]="form.invalid || saving()">
              {{
                saving()
                  ? walk
                    ? 'Enregistrement…'
                    : 'Publication…'
                  : walk
                    ? 'Enregistrer'
                    : 'Publier'
              }}
            </button>
          } @else {
            <button type="button" class="btn-create" (click)="finishAfterPhotos()">
              Terminer
            </button>
          }
        </div>
      </form>
    </div>
  `,
  styles: [
    `
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(26, 31, 26, 0.45);
        z-index: 40;
      }
      .modal {
        position: fixed;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%);
        width: min(28rem, calc(100vw - 1.5rem));
        max-height: min(90vh, 40rem);
        overflow: auto;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-md);
        z-index: 41;
        padding: 1.1rem 1.15rem 1.25rem;
      }
      .modal-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
      }
      .modal-head h2 {
        margin: 0;
        font-size: 1.15rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .modal-close {
        border: none;
        background: transparent;
        font-size: 1.5rem;
        line-height: 1;
        color: var(--muted);
        cursor: pointer;
        padding: 0.15rem 0.35rem;
      }
      .modal-sub {
        margin: 0.35rem 0 0.85rem;
        color: var(--muted);
        font-size: 0.88rem;
      }
      .banner.error {
        margin: 0 0 0.75rem;
        padding: 0.55rem 0.7rem;
        border-radius: var(--radius-sm);
        background: #fdeceb;
        color: var(--danger);
        font-size: 0.88rem;
      }
      .create-form {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .create-form label {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      .create-form input,
      .create-form select,
      .create-form textarea {
        font-weight: 400;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        padding: 0.55rem 0.65rem;
        background: var(--bg);
        font-size: 0.92rem;
      }
      .form-row {
        display: grid;
        grid-template-columns: 1.4fr 0.8fr;
        gap: 0.65rem;
      }
      fieldset {
        margin: 0;
        padding: 0.55rem 0.65rem 0.7rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
      }
      legend {
        padding: 0 0.25rem;
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      .chip-row {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.82rem;
        font-weight: 500;
        color: var(--ink);
        background: var(--surface-soft);
        border-radius: var(--radius-pill);
        padding: 0.25rem 0.65rem;
      }
      .field-hint {
        margin: 0;
        font-size: 0.8rem;
        color: var(--muted);
      }
      .difficulty-preview {
        margin: 0;
        padding: 0.65rem 0.75rem;
        border-radius: var(--radius-sm);
        background: var(--surface-soft);
        color: var(--forest-deep);
        font-size: 0.88rem;
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }
      .difficulty-preview strong {
        font-family: var(--font-display);
      }
      .route-block {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .route-label {
        font-size: 0.82rem;
        font-weight: 600;
        color: var(--forest-deep);
      }
      .modal-actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.55rem;
        margin-top: 0.35rem;
      }
      .btn-ghost {
        background: transparent;
        border: 1px solid var(--line);
        border-radius: var(--radius-pill);
        padding: 0.5rem 0.95rem;
        font-size: 0.88rem;
        font-weight: 600;
        cursor: pointer;
        color: var(--muted);
      }
      .btn-ghost:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .btn-create {
        background: var(--forest);
        color: var(--on-forest);
        border: none;
        border-radius: var(--radius-pill);
        padding: 0.55rem 1.1rem;
        font-size: 0.9rem;
        font-weight: 700;
        font-family: var(--font-display);
        cursor: pointer;
      }
      .btn-create:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
    `,
  ],
})
export class CommunityWalkModalComponent implements OnInit, OnChanges {
  @Input({ required: true }) city!: City;
  @Input() walk: CommunityWalkEditModel | null = null;

  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly saved = new EventEmitter<void>();

  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  protected readonly sizeOptions = SIZE_OPTIONS;
  protected readonly energyOptions = ENERGY_OPTIONS;
  protected readonly terrainOptions = TERRAIN_OPTIONS;

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly meetingPoints = signal<MeetingPointOption[]>([]);
  protected readonly meetingPointsLoading = signal(false);
  protected readonly placingMeetingPoint = signal(false);
  protected readonly selectedMeetingPointId = signal<number | null>(null);
  protected readonly draftRoute = signal<RouteGeoJson | null>(null);
  protected readonly routeEditorCenter = signal<{ lat: number; lng: number } | null>(null);
  protected readonly durationFromRoute = signal(false);
  protected readonly photoWalkId = signal<number | null>(null);
  protected readonly promoPhotos = signal<PhotoItem[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', Validators.maxLength(800)],
    meetingPointId: ['', Validators.required],
    startsAt: ['', Validators.required],
    durationMinutes: [45, [Validators.required, Validators.min(15)]],
    elevationGainM: [0, [Validators.required, Validators.min(0), Validators.max(3000)]],
    terrainType: ['PARK', Validators.required],
    capacity: [6, [Validators.required, Validators.min(1), Validators.max(8)]],
    acceptedSizes: this.fb.nonNullable.control<string[]>(['SMALL', 'MEDIUM', 'LARGE'], [
      Validators.required,
      Validators.minLength(1),
    ]),
    acceptedEnergy: this.fb.nonNullable.control<string[]>(['LOW', 'MEDIUM', 'HIGH'], [
      Validators.required,
      Validators.minLength(1),
    ]),
    minSociabilityDogs: ['AVERAGE', Validators.required],
    routeHint: ['', Validators.maxLength(200)],
  });

  protected previewDifficulty(): number {
    const v = this.form.getRawValue();
    return estimateDifficultyLevel(v.durationMinutes || 15, v.elevationGainM || 0, v.terrainType);
  }

  protected difficultyHint(level: number): string {
    return difficultyLabel(level);
  }

  protected get cityCenter(): { lat: number; lng: number } | null {
    const city = this.city;
    if (
      city?.lat == null ||
      city.lng == null ||
      !Number.isFinite(city.lat) ||
      !Number.isFinite(city.lng)
    ) {
      return null;
    }
    return { lat: city.lat, lng: city.lng };
  }

  ngOnInit(): void {
    this.resetFromInputs();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['walk'] || changes['city']) {
      this.resetFromInputs();
    }
  }

  protected onClose(): void {
    if (this.saving()) {
      return;
    }
    if (this.photoWalkId() && !this.walk) {
      this.saved.emit();
    }
    this.closed.emit();
  }

  protected onDraftRoute(route: RouteGeoJson | null): void {
    this.draftRoute.set(route);
    if (!route?.coordinates?.length) {
      this.durationFromRoute.set(false);
      return;
    }
    const minutes = estimateWalkMinutes(lineLengthMeters(route.coordinates));
    if (minutes <= 0) {
      this.durationFromRoute.set(false);
      return;
    }
    this.form.controls.durationMinutes.setValue(minutes);
    this.form.controls.durationMinutes.markAsDirty();
    this.durationFromRoute.set(true);
  }

  protected onMeetingPointPicked(id: number): void {
    this.selectedMeetingPointId.set(id);
    this.form.controls.meetingPointId.setValue(String(id));
    this.form.controls.meetingPointId.markAsDirty();
    this.syncRouteEditorCenter();
  }

  protected onMapPlaceMeetingPoint(pos: { lat: number; lng: number }): void {
    if (this.placingMeetingPoint()) {
      return;
    }
    this.placingMeetingPoint.set(true);
    const rounded = `${pos.lat.toFixed(5)}, ${pos.lng.toFixed(5)}`;
    this.http
      .post<MeetingPointOption>('/api/meeting-points', {
        cityId: this.city.id,
        name: `Rendez-vous ${this.city.name}`,
        address: rounded,
        lat: pos.lat,
        lng: pos.lng,
      })
      .subscribe({
        next: (created) => {
          this.meetingPoints.update((list) => [...list, created]);
          this.onMeetingPointPicked(created.id);
          this.placingMeetingPoint.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.placingMeetingPoint.set(false);
          this.error.set(this.errorMessage(err) || 'Impossible de créer le point de rendez-vous.');
        },
      });
  }

  protected hasSize(value: string): boolean {
    return this.form.controls.acceptedSizes.value.includes(value);
  }

  protected hasEnergy(value: string): boolean {
    return this.form.controls.acceptedEnergy.value.includes(value);
  }

  protected toggleSize(value: string): void {
    const current = [...this.form.controls.acceptedSizes.value];
    const idx = current.indexOf(value);
    if (idx >= 0) {
      current.splice(idx, 1);
    } else {
      current.push(value);
    }
    this.form.controls.acceptedSizes.setValue(current);
    this.form.controls.acceptedSizes.markAsDirty();
  }

  protected toggleEnergy(value: string): void {
    const current = [...this.form.controls.acceptedEnergy.value];
    const idx = current.indexOf(value);
    if (idx >= 0) {
      current.splice(idx, 1);
    } else {
      current.push(value);
    }
    this.form.controls.acceptedEnergy.setValue(current);
    this.form.controls.acceptedEnergy.markAsDirty();
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const meetingPointId = Number(v.meetingPointId);
    if (!Number.isFinite(meetingPointId) || v.acceptedSizes.length === 0 || v.acceptedEnergy.length === 0) {
      this.error.set('Complétez les champs obligatoires.');
      return;
    }
    const startsAt = new Date(v.startsAt);
    if (Number.isNaN(startsAt.getTime())) {
      this.error.set('Date de départ invalide.');
      return;
    }

    this.saving.set(true);
    this.error.set(null);

    const payloadBase = {
      meetingPointId,
      title: v.title.trim(),
      description: v.description.trim() || null,
      startsAt: startsAt.toISOString(),
      durationMinutes: v.durationMinutes,
      elevationGainM: v.elevationGainM,
      terrainType: v.terrainType,
      capacity: v.capacity,
      acceptedSizes: v.acceptedSizes,
      acceptedEnergy: v.acceptedEnergy,
      minSociabilityDogs: v.minSociabilityDogs,
      routeHint: v.routeHint.trim() || null,
      routeGeojson: this.draftRoute(),
    };

    const editing = !!this.walk;
    const request$ = editing
      ? this.http.put<{ id: number; promoPhotoUrls?: string[] }>(`/api/walks/${this.walk!.id}`, payloadBase)
      : this.http.post<{ id: number; promoPhotoUrls?: string[] }>('/api/walks', {
          ...payloadBase,
          cityId: this.city.id,
          walkType: 'COMMUNITY',
          priceCents: 0,
          nonMemberPriceCents: null,
          walkerId: null,
          allowsDangerCategories: false,
          communityDisclaimerRequired: true,
        });

    request$.subscribe({
      next: (walk) => {
        this.saving.set(false);
        this.photoWalkId.set(walk.id);
        this.loadPromoPhotos(walk.id);
        if (editing) {
          this.saved.emit();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.errorMessage(err));
      },
    });
  }

  protected finishAfterPhotos(): void {
    this.saved.emit();
    this.closed.emit();
  }

  private loadPromoPhotos(walkId: number): void {
    this.http
      .get<Array<{ id: number; url: string; sortOrder: number }>>(
        `/api/walks/${walkId}/photos`,
        { params: { kind: 'PROMO' } },
      )
      .subscribe({
        next: (photos) =>
          this.promoPhotos.set(
            photos.map((p) => ({ id: p.id, url: p.url, sortOrder: p.sortOrder })),
          ),
        error: () => this.promoPhotos.set([]),
      });
  }

  private resetFromInputs(): void {
    this.error.set(null);
    this.saving.set(false);
    this.placingMeetingPoint.set(false);
    this.durationFromRoute.set(false);
    this.promoPhotos.set([]);
    const walk = this.walk;
    if (walk) {
      this.photoWalkId.set(walk.id);
      this.loadPromoPhotos(walk.id);
      this.draftRoute.set(parseRouteGeoJson(walk.routeGeojson));
      this.selectedMeetingPointId.set(walk.meetingPointId);
      this.routeEditorCenter.set(
        walk.lat != null && walk.lng != null
          ? { lat: walk.lat, lng: walk.lng }
          : this.cityCenter,
      );
      this.form.reset({
        title: walk.title ?? '',
        description: walk.description ?? '',
        meetingPointId: String(walk.meetingPointId),
        startsAt: toDatetimeLocalValue(walk.startsAt),
        durationMinutes: walk.durationMinutes || 45,
        elevationGainM: walk.elevationGainM ?? 0,
        terrainType: walk.terrainType || 'PARK',
        capacity: walk.capacity || 6,
        acceptedSizes: walk.acceptedSizes?.length
          ? [...walk.acceptedSizes]
          : ['SMALL', 'MEDIUM', 'LARGE'],
        acceptedEnergy: walk.acceptedEnergy?.length
          ? [...walk.acceptedEnergy]
          : ['LOW', 'MEDIUM', 'HIGH'],
        minSociabilityDogs: walk.minSociabilityDogs || 'AVERAGE',
        routeHint: walk.routeHint ?? '',
      });
      this.loadMeetingPoints(this.city.id, walk.meetingPointId);
      return;
    }

    this.photoWalkId.set(null);
    this.draftRoute.set(null);
    this.selectedMeetingPointId.set(null);
    this.routeEditorCenter.set(this.cityCenter);
    this.form.reset({
      title: '',
      description: '',
      meetingPointId: '',
      startsAt: defaultStartsAtLocal(),
      durationMinutes: 45,
      elevationGainM: 0,
      terrainType: 'PARK',
      capacity: 6,
      acceptedSizes: ['SMALL', 'MEDIUM', 'LARGE'],
      acceptedEnergy: ['LOW', 'MEDIUM', 'HIGH'],
      minSociabilityDogs: 'AVERAGE',
      routeHint: '',
    });
    this.loadMeetingPoints(this.city.id);
  }

  private loadMeetingPoints(cityId: number, preferMeetingPointId?: number): void {
    this.meetingPointsLoading.set(true);
    this.meetingPoints.set([]);
    this.http
      .get<MeetingPointOption[]>('/api/meeting-points', { params: { cityId: cityId.toString() } })
      .subscribe({
        next: (points) => {
          this.meetingPoints.set(points);
          this.meetingPointsLoading.set(false);
          if (preferMeetingPointId && points.some((p) => p.id === preferMeetingPointId)) {
            this.onMeetingPointPicked(preferMeetingPointId);
          } else if (!this.walk && points.length === 1) {
            this.onMeetingPointPicked(points[0].id);
          } else {
            this.syncRouteEditorCenter();
          }
        },
        error: () => {
          this.meetingPointsLoading.set(false);
          this.error.set('Impossible de charger les points de rendez-vous.');
        },
      });
  }

  private syncRouteEditorCenter(): void {
    const mpId = Number(this.form.controls.meetingPointId.value);
    const mp = this.meetingPoints().find((p) => p.id === mpId);
    if (mp && Number.isFinite(mp.lat) && Number.isFinite(mp.lng)) {
      this.routeEditorCenter.set({ lat: mp.lat, lng: mp.lng });
      return;
    }
    this.routeEditorCenter.set(this.cityCenter);
  }

  private errorMessage(err: HttpErrorResponse): string {
    if (err.status === 401) {
      return 'Connectez-vous pour proposer une balade.';
    }
    if (err.status === 403) {
      return this.walk
        ? 'Seul le créateur peut modifier cette balade.'
        : 'Droits organisateur communautaire requis.';
    }
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
    return this.walk
      ? 'La modification de la balade a échoué.'
      : 'La création de la balade a échoué.';
  }
}

function defaultStartsAtLocal(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(10, 0, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toDatetimeLocalValue(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function parseRouteGeoJson(raw: CommunityWalkEditModel['routeGeojson']): RouteGeoJson | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed = typeof raw === 'string' ? (JSON.parse(raw) as RouteGeoJson) : raw;
    if (
      parsed?.type === 'LineString' &&
      Array.isArray(parsed.coordinates) &&
      parsed.coordinates.length >= 2
    ) {
      return parsed;
    }
  } catch {
    return null;
  }
  return null;
}
