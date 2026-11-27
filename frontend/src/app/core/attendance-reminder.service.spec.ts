import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AttendanceReminderService } from './attendance-reminder.service';
import { AuthService } from './auth.service';

describe('AttendanceReminderService', () => {
  let service: AttendanceReminderService;
  let http: HttpTestingController;
  const loggedIn = signal(true);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            loggedIn: () => loggedIn(),
          },
        },
      ],
    });
    service = TestBed.inject(AttendanceReminderService);
    http = TestBed.inject(HttpTestingController);
    // Vide d’éventuelles requêtes issues du timer initial.
    for (const req of http.match('/api/attendance/pending')) {
      req.flush([]);
    }
  });

  afterEach(() => {
    service.ngOnDestroy();
    for (const req of http.match('/api/attendance/pending')) {
      req.flush([]);
    }
    http.verify();
  });

  // Charge les pointages en attente pour un compte connecté.
  it('récupère les balades à pointer', () => {
    service.refresh();
    const req = http.expectOne('/api/attendance/pending');
    req.flush([
      {
        walkId: 1,
        title: 'Pointage',
        startsAt: '2026-11-27T01:00:00Z',
        durationMinutes: 60,
        endsAt: '2026-11-27T02:00:00Z',
        status: 'IN_PROGRESS',
        confirmedCount: 2,
      },
    ]);
    expect(service.pendingCount()).toBe(1);
    expect(service.pending()[0].title).toBe('Pointage');
  });

  // Vide le rappel si la session est absente.
  it('ignore le polling hors session', () => {
    loggedIn.set(false);
    service.refresh();
    expect(service.pendingCount()).toBe(0);
  });
});
