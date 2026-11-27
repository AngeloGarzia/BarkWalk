// Réserve l’agenda aux comptes admin ou organisateur communautaire.
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const calendarGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn() && auth.canAccessCalendar()) {
    return true;
  }
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  return router.createUrlTree(['/']);
};
