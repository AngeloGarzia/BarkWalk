// Sélecteur 1–3 photos : galerie (fichier) ou caméra mobile.
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface PhotoItem {
  id: number;
  url: string;
  sortOrder: number;
}

@Component({
  selector: 'app-photo-uploader',
  standalone: true,
  template: `
    <div class="photos">
      <div class="photos-head">
        <h3>{{ title }}</h3>
        <span class="count">{{ photos().length }} / {{ max }}</span>
      </div>

      @if (error()) {
        <p class="banner error" role="alert">{{ error() }}</p>
      }

      <ul class="grid">
        @for (photo of photos(); track photo.id) {
          <li class="slot filled">
            <img [src]="photo.url" [alt]="title + ' ' + (photo.sortOrder + 1)" />
            <button type="button" class="remove" (click)="remove(photo.id)" aria-label="Supprimer la photo">
              ×
            </button>
          </li>
        }
        @if (photos().length < max) {
          <li class="slot empty">
            <div class="actions">
              <label class="pick">
                Fichier
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  (change)="onFile($event)"
                  [disabled]="uploading()"
                />
              </label>
              <label class="pick camera">
                Caméra
                <input
                  type="file"
                  accept="image/*"
                  capture="environment"
                  (change)="onFile($event)"
                  [disabled]="uploading()"
                />
              </label>
            </div>
            @if (uploading()) {
              <span class="hint">Envoi…</span>
            } @else {
              <span class="hint">JPEG / PNG / WebP · max 5 Mo</span>
            }
          </li>
        }
      </ul>
    </div>
  `,
  styles: [
    `
      .photos {
        margin: 0.75rem 0 1rem;
      }
      .photos-head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        margin-bottom: 0.55rem;
      }
      h3 {
        margin: 0;
        font-size: 0.95rem;
        color: var(--forest-deep);
        font-family: var(--font-display);
      }
      .count {
        font-size: 0.8rem;
        color: var(--muted);
        font-weight: 600;
      }
      .grid {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.55rem;
      }
      .slot {
        aspect-ratio: 1;
        border-radius: var(--radius-md);
        border: 1px dashed var(--line);
        background: var(--bg);
        overflow: hidden;
        position: relative;
        display: grid;
        place-items: center;
      }
      .slot.filled {
        border-style: solid;
      }
      .slot img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
      }
      .remove {
        position: absolute;
        top: 0.3rem;
        right: 0.3rem;
        width: 1.6rem;
        height: 1.6rem;
        border: none;
        border-radius: 50%;
        background: rgba(26, 31, 26, 0.72);
        color: #fff;
        font-size: 1.1rem;
        line-height: 1;
        cursor: pointer;
      }
      .actions {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        width: 100%;
        padding: 0.45rem;
      }
      .pick {
        position: relative;
        display: grid;
        place-items: center;
        min-height: 1.85rem;
        border-radius: var(--radius-pill);
        background: var(--forest);
        color: var(--on-forest);
        font-size: 0.72rem;
        font-weight: 700;
        cursor: pointer;
      }
      .pick.camera {
        background: var(--cta);
        color: var(--on-cta);
      }
      .pick input {
        position: absolute;
        inset: 0;
        opacity: 0;
        cursor: pointer;
      }
      .hint {
        font-size: 0.65rem;
        color: var(--muted);
        text-align: center;
        padding: 0 0.35rem 0.4rem;
      }
      .banner.error {
        background: #fde8e8;
        color: var(--danger);
        padding: 0.5rem 0.7rem;
        border-radius: var(--radius-sm);
        font-size: 0.82rem;
        margin: 0 0 0.5rem;
      }
    `,
  ],
})
export class PhotoUploaderComponent {
  private readonly http = inject(HttpClient);

  @Input({ required: true }) uploadUrl!: string;
  @Input() deleteUrlBase?: string;
  @Input() title = 'Photos';
  @Input() max = 3;
  @Input() set initialPhotos(value: PhotoItem[] | null | undefined) {
    this.photos.set([...(value ?? [])]);
  }

  @Output() readonly photosChange = new EventEmitter<PhotoItem[]>();

  protected readonly photos = signal<PhotoItem[]>([]);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (this.photos().length >= this.max) {
      this.error.set(`Maximum ${this.max} photos`);
      return;
    }
    if (!/^image\/(jpeg|jpg|png|webp)$/i.test(file.type) && !file.type.startsWith('image/')) {
      this.error.set('Formats acceptés : JPEG, PNG, WebP');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.error.set('Image trop lourde (max 5 Mo)');
      return;
    }

    const body = new FormData();
    body.append('file', file, file.name || 'photo.jpg');
    this.uploading.set(true);
    this.error.set(null);
    this.http.post<PhotoItem>(this.uploadUrl, body).subscribe({
      next: (photo) => {
        const next = [...this.photos(), photo].sort((a, b) => a.sortOrder - b.sortOrder);
        this.photos.set(next);
        this.photosChange.emit(next);
        this.uploading.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message || err?.error?.detail || 'Envoi impossible';
        this.error.set(typeof msg === 'string' ? msg : 'Envoi impossible');
        this.uploading.set(false);
      },
    });
  }

  protected remove(photoId: number): void {
    const base = this.deleteUrlBase ?? this.uploadUrl;
    this.http.delete(`${base}/${photoId}`).subscribe({
      next: () => {
        const next = this.photos().filter((p) => p.id !== photoId);
        this.photos.set(next);
        this.photosChange.emit(next);
      },
      error: () => this.error.set('Suppression impossible'),
    });
  }
}
