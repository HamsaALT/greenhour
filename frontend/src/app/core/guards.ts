import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { Auth } from './auth';

export const signedIn: CanActivateFn = async (_route, state) => {
  const auth = inject(Auth);
  await auth.ready();
  if (auth.isLoggedIn()) return true;
  await auth.login(state.url);
  return false;
};

export const adminOnly: CanActivateFn = async () => {
  const auth = inject(Auth);
  await auth.ready();
  return auth.isAdmin();
};
