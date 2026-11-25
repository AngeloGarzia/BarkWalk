// Ajoute le Bearer JWT aux appels API et gère le refresh basique.
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  // JWT uniquement pour notre API — pas pour les appels externes (ex. dogapi.dog).
  const isAppApi = req.url.startsWith('/api') || req.url.startsWith('/actuator');
  if (token && isAppApi) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};
