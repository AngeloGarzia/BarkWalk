// Rappel pointage : balades dont l'heure est dépassée sans validation des présents.
import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Subscription, catchError, of, switchMap, timer } from 'rxjs';
import { AuthService } from './auth.service';

export interface PendingRollcallWalk {
  walkId: number;
  title: string;
  cityName?: string | null;
  startsAt: string;
  durationMinutes: number;
  endsAt: string;
  status: string;
  confirmedCount: number;
}

@Injectable({ providedIn: 'root' })
export class AttendanceReminderService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly pending = signal<PendingRollcallWalk[]>([]);
  readonly pendingCount = signal(0);
  readonly loading = signal(false);

  private sub?: Subscription;

  constructor() {
    this.sub = timer(0, 60_000)
      .pipe(
        switchMap(() => {
          if (!this.auth.loggedIn()) {
            this.pending.set([]);
            this.pendingCount.set(0);
            return of([] as PendingRollcallWalk[]);
          }
          this.loading.set(true);
          return this.http.get<PendingRollcallWalk[]>('/api/attendance/pending').pipe(
            catchError(() => of([] as PendingRollcallWalk[])),
          );
        }),
      )
      .subscribe((list) => {
        this.pending.set(list);
        this.pendingCount.set(list.length);
        this.loading.set(false);
      });
  }

  refresh(): void {
    if (!this.auth.loggedIn()) {
      this.pending.set([]);
      this.pendingCount.set(0);
      return;
    }
    this.loading.set(true);
    this.http.get<PendingRollcallWalk[]>('/api/attendance/pending').subscribe({
      next: (list) => {
        this.pending.set(list);
        this.pendingCount.set(list.length);
        this.loading.set(false);
      },
      error: () => {
        this.pending.set([]);
        this.pendingCount.set(0);
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
