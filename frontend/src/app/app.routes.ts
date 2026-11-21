import { Routes } from '@angular/router';
import { adminOnly, signedIn } from './core/guards';

export const routes: Routes = [
  {
    path: '',
    title: 'titles.prices',
    loadComponent: () => import('./features/prices/prices-page').then((m) => m.PricesPage),
  },
  {
    path: 'plan',
    title: 'titles.plan',
    canActivate: [signedIn],
    loadComponent: () => import('./features/plan/plan-page').then((m) => m.PlanPage),
  },
  {
    path: 'home',
    title: 'titles.home',
    canActivate: [signedIn],
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    path: 'history',
    title: 'titles.history',
    canActivate: [signedIn],
    loadComponent: () => import('./features/history/history-page').then((m) => m.HistoryPage),
  },
  {
    path: 'admin',
    title: 'titles.admin',
    canActivate: [signedIn, adminOnly],
    loadComponent: () => import('./features/admin/admin-page').then((m) => m.AdminPage),
  },
  { path: '**', redirectTo: '' },
];
