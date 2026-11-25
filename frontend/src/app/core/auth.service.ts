// État de session côté navigateur : jetons, rôles, login et logout.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { rolesFromToken } from './jwt';

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

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private _token: string | null = localStorage.getItem(ACCESS_KEY);
  readonly loggedIn = signal(!!this._token);
  // Relu depuis le JWT pour survivre à un rechargement de page.
  readonly roles = signal<string[]>(rolesFromToken(this._token));

  getToken(): string | null {
    return this._token;
  }

  isLoggedIn(): boolean {
    return !!this._token;
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', payload).pipe(tap((res) => this.store(res)));
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', payload)
      .pipe(tap((res) => this.store(res)));
  }

  logout(): void {
    const refresh = localStorage.getItem(REFRESH_KEY);
    if (refresh) {
      this.http.post('/api/auth/logout', { refreshToken: refresh }).subscribe({ error: () => undefined });
    }
    this._token = null;
    this.loggedIn.set(false);
    this.roles.set([]);
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  private store(res: AuthResponse): void {
    this._token = res.accessToken;
    this.loggedIn.set(true);
    this.roles.set(res.roles ?? []);
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
  }
}
