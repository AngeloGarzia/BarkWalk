// État de session côté navigateur : jetons, rôles, login et logout.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, shareReplay, tap, throwError } from 'rxjs';
import { decodeJwtPayload } from './jwt.util';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMinutes: number;
  roles: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  cityId?: number | null;
  acceptCgu: boolean;
}

const ACCESS_KEY = 'bw_access_token';
const REFRESH_KEY = 'bw_refresh_token';

function rolesFromJwt(token: string | null): string[] {
  if (!token) {
    return [];
  }
  const payload = decodeJwtPayload(token);
  if (!payload) {
    return [];
  }
  const roles = payload['roles'];
  if (Array.isArray(roles)) {
    return roles.filter((r): r is string => typeof r === 'string');
  }
  return [];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private _token: string | null = localStorage.getItem(ACCESS_KEY);
  readonly loggedIn = signal(!!this._token);
  readonly roles = signal<string[]>(rolesFromJwt(this._token));

  /** Shared in-flight refresh so concurrent 401s only rotate once. */
  private refresh$?: Observable<AuthResponse>;

  getToken(): string | null {
    return this._token;
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  isLoggedIn(): boolean {
    return !!this._token;
  }

  hasRole(role: string): boolean {
    return this.roles().includes(role);
  }

  isAdmin(): boolean {
    return this.hasRole('ADMIN');
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', payload).pipe(tap((res) => this.store(res)));
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', payload)
      .pipe(tap((res) => this.store(res)));
  }

  refreshSession(): Observable<AuthResponse> {
    if (!this.refresh$) {
      const refreshToken = this.getRefreshToken();
      if (!refreshToken) {
        return throwError(() => new Error('No refresh token'));
      }
      this.refresh$ = this.http
        .post<AuthResponse>('/api/auth/refresh', { refreshToken })
        .pipe(
          tap({
            next: (res) => this.store(res),
            error: () => {
              this.refresh$ = undefined;
            },
            complete: () => {
              this.refresh$ = undefined;
            },
          }),
          shareReplay({ bufferSize: 1, refCount: true }),
        );
    }
    return this.refresh$;
  }

  logout(): void {
    const refresh = localStorage.getItem(REFRESH_KEY);
    if (refresh) {
      this.http.post('/api/auth/logout', { refreshToken: refresh }).subscribe({ error: () => undefined });
    }
    this._token = null;
    this.loggedIn.set(false);
    this.roles.set([]);
    this.refresh$ = undefined;
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  private store(res: AuthResponse): void {
    this._token = res.accessToken;
    this.loggedIn.set(true);
    const fromResponse = res.roles ?? [];
    this.roles.set(fromResponse.length ? fromResponse : rolesFromJwt(res.accessToken));
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
  }
}
