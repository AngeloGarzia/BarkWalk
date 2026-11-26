// Ajoute le Bearer JWT aux appels API et rafraîchit la session sur 401 / JWT expiré.
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { decodeJwtPayload } from './jwt.util';

function isJwtExpired(token: string | null): boolean {
  if (!token) {
    return true;
  }
  const payload = decodeJwtPayload(token);
  if (!payload) {
    return true;
  }
  const exp = payload['exp'];
  return typeof exp === 'number' && exp * 1000 < Date.now();
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  // JWT uniquement pour notre API — pas pour les appels externes (ex. dogapi.dog).
  const isAppApi = req.url.startsWith('/api') || req.url.startsWith('/actuator');
  const isAuthEndpoint = req.url.startsWith('/api/auth/');

  const withBearer = () => {
    const token = authService.getToken();
    if (token && isAppApi && !isAuthEndpoint) {
      return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
    }
    return req;
  };

  return next(withBearer()).pipe(
    catchError((err: HttpErrorResponse) => {
      const expired = isJwtExpired(authService.getToken());
      const canRefresh =
        isAppApi &&
        !isAuthEndpoint &&
        !!authService.getRefreshToken() &&
        (err.status === 401 || (err.status === 403 && expired));

      if (!canRefresh) {
        return throwError(() => err);
      }

      // catchError only on refresh — replay errors propagate unchanged (no logout).
      return authService.refreshSession().pipe(
        catchError((refreshErr) => {
          authService.logout();
          void router.navigate(['/login']);
          return throwError(() => refreshErr);
        }),
        switchMap(() => next(withBearer())),
      );
    }),
  );
};
