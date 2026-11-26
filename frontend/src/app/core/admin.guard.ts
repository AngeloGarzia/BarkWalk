// Réserve les routes admin aux comptes avec rôle ADMIN.
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn() && auth.isAdmin()) {
    return true;
  }
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  return router.createUrlTree(['/']);
};
