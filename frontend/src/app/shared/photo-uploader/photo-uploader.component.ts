// Sélecteur 1–3 photos : galerie (fichier) ou caméra (getUserMedia).
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';

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
              <button
                type="button"
                class="pick camera"
                (click)="openCamera()"
                [disabled]="uploading() || cameraOpen()"
              >
                Caméra
              </button>
            </div>
            @if (uploading()) {
              <span class="hint">Envoi…</span>
            } @else {
              <span class="hint">JPEG / PNG / WebP · max 5 Mo</span>
            }
          </li>
        }
      </ul>

      @if (cameraOpen()) {
        <div class="camera-overlay" role="dialog" aria-modal="true" aria-label="Prise de photo">
          <div class="camera-panel">
            @if (videoDevices().length > 0) {
              <label class="device-pick">
                Caméra
                <select
                  [value]="selectedDeviceId()"
                  (change)="onDeviceChange($event)"
                  aria-label="Choisir le périphérique caméra"
                >
                  @for (device of videoDevices(); track device.deviceId) {
                    <option [value]="device.deviceId">{{ deviceLabel(device, $index) }}</option>
                  }
                </select>
              </label>
            }
            <video #preview autoplay playsinline muted></video>
            <canvas #snap hidden></canvas>
            <div class="camera-actions">
              <button type="button" class="pick camera" (click)="takePhoto()" [disabled]="uploading()">
                Prendre la photo
              </button>
              <button type="button" class="pick cancel" (click)="closeCamera()">Annuler</button>
            </div>
          </div>
        </div>
      }
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
        background: rgba(26, 31, 26,.72);
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
        border: none;
        border-radius: var(--radius-pill);
        background: var(--forest);
        color: var(--on-forest);
        font-size: 0.72rem;
        font-weight: 700;
        cursor: pointer;
        width: 100%;
        font-family: inherit;
      }
      .pick.camera {
        background: var(--cta);
        color: var(--on-cta);
      }
      .pick.cancel {
        background: transparent;
        color: var(--muted);
        border: 1px solid var(--line);
      }
      .pick:disabled {
        opacity: 0.55;
        cursor: not-allowed;
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
      .camera-overlay {
        position: fixed;
        inset: 0;
        z-index: 80;
        background: rgba(20, 24, 20, 0.72);
        display: grid;
        place-items: center;
        padding: 1rem;
      }
      .camera-panel {
        width: min(28rem, 100%);
        background: var(--surface, #fff);
        border-radius: var(--radius-md);
        padding: 0.75rem;
        box-shadow: var(--shadow-sm);
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }
      .camera-panel video {
        width: 100%;
        max-height: 60vh;
        border-radius: var(--radius-sm);
        background: #111;
        object-fit: cover;
      }
      .device-pick {
        display: grid;
        gap: 0.3rem;
        font-size: 0.78rem;
        font-weight: 700;
        color: var(--forest-deep);
      }
      .device-pick select {
        width: 100%;
        padding: 0.45rem 0.55rem;
        border: 1px solid var(--line);
        border-radius: var(--radius-sm);
        font: inherit;
        background: var(--bg, #fff);
        color: inherit;
      }
      .camera-actions {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }
    `,
  ],
})
export class PhotoUploaderComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('preview') private previewRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('snap') private snapRef?: ElementRef<HTMLCanvasElement>;

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
  protected readonly cameraOpen = signal(false);
  protected readonly videoDevices = signal<MediaDeviceInfo[]>([]);
  protected readonly selectedDeviceId = signal<string>('');

  private stream: MediaStream | null = null;
  private openingCamera = false;

  ngOnDestroy(): void {
    this.stopStream();
  }

  protected async openCamera(): Promise<void> {
    if (this.openingCamera || this.cameraOpen() || this.uploading()) {
      return;
    }
    this.openingCamera = true;
    this.error.set(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      this.openingCamera = false;
      this.error.set('Caméra non disponible sur cet appareil.');
      return;
    }
    try {
      await this.startStream(this.selectedDeviceId() || undefined);
      await this.refreshVideoDevices();
      this.cameraOpen.set(true);
      this.cdr.detectChanges();
      await this.attachPreview();
    } catch {
      this.closeCamera();
      this.error.set('Impossible d’accéder à la caméra (permission refusée ?).');
    } finally {
      this.openingCamera = false;
    }
  }

  protected async onDeviceChange(event: Event): Promise<void> {
    const deviceId = (event.target as HTMLSelectElement).value;
    this.selectedDeviceId.set(deviceId);
    try {
      await this.startStream(deviceId);
      this.cdr.detectChanges();
      await this.attachPreview();
    } catch {
      this.error.set('Impossible de basculer sur cette caméra.');
    }
  }

  protected deviceLabel(device: MediaDeviceInfo, index: number): string {
    const label = device.label?.trim();
    if (label) {
      return label;
    }
    return `Caméra ${index + 1}`;
  }

  protected closeCamera(): void {
    this.stopStream();
    this.cameraOpen.set(false);
    this.videoDevices.set([]);
  }

  protected takePhoto(): void {
    const video = this.previewRef?.nativeElement;
    const canvas = this.snapRef?.nativeElement;
    if (!video || !canvas || !video.videoWidth) {
      this.error.set('Flux caméra pas encore prêt.');
      return;
    }
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      this.error.set('Capture impossible.');
      return;
    }
    ctx.drawImage(video, 0, 0);
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          this.error.set('Capture impossible.');
          return;
        }
        this.closeCamera();
        const file = new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' });
        this.uploadFile(file);
      },
      'image/jpeg',
      0.92,
    );
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.uploadFile(file);
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

  private uploadFile(file: File): void {
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
    this.auth.ensureFreshAccessToken().subscribe((ok) => {
      if (!ok) {
        this.uploading.set(false);
        this.error.set('Session expirée — reconnectez-vous pour envoyer la photo.');
        return;
      }
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
    });
  }

  private async startStream(deviceId?: string): Promise<void> {
    this.stopStream();
    const video: MediaTrackConstraints = deviceId
      ? { deviceId: { exact: deviceId } }
      : {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        };
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video,
    });
    const activeId = this.stream.getVideoTracks()[0]?.getSettings()?.deviceId;
    if (activeId) {
      this.selectedDeviceId.set(activeId);
    }
  }

  private async attachPreview(): Promise<void> {
    const video = this.previewRef?.nativeElement;
    if (!video || !this.stream) {
      return;
    }
    video.srcObject = this.stream;
    await video.play().catch(() => undefined);
  }

  private async refreshVideoDevices(): Promise<void> {
    const devices = await navigator.mediaDevices.enumerateDevices();
    const cameras = devices.filter((d) => d.kind === 'videoinput');
    this.videoDevices.set(cameras);
    if (!this.selectedDeviceId() && cameras[0]?.deviceId) {
      this.selectedDeviceId.set(cameras[0].deviceId);
    }
  }

  private stopStream(): void {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    const video = this.previewRef?.nativeElement;
    if (video) {
      video.srcObject = null;
    }
  }
}
