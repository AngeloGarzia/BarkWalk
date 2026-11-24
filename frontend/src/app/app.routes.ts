import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'catalog',
    loadComponent: () =>
      import('./features/catalog/catalog.component').then(m => m.CatalogComponent),
  },
  {
    path: 'dogs',
    loadComponent: () => import('./features/dogs/dogs.component').then(m => m.DogsComponent),
    canActivate: [authGuard],
  },
  {
    path: 'profile',
    loadComponent: () =>
      import('./features/profile/profile.component').then(m => m.ProfileComponent),
    canActivate: [authGuard],
  },
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent),
    canActivate: [authGuard],
  },
  {
    path: 'walker/today',
    loadComponent: () =>
      import('./features/walker/walker-today.component').then(m => m.WalkerTodayComponent),
    canActivate: [authGuard],
  },
  {
    path: 'booking',
    loadComponent: () =>
      import('./features/booking/booking.component').then(m => m.BookingComponent),
    canActivate: [authGuard],
  },
  {
    path: 'messaging',
    loadComponent: () =>
      import('./features/messaging/messaging.component').then(m => m.MessagingComponent),
    canActivate: [authGuard],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
