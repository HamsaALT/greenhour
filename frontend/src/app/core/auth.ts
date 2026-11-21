import { Injectable, computed, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from './environment';

/** How long the first render may wait for Keycloak before showing the guest view. */
export const AUTH_TIMEOUT_MS = 2500;
import { currentLang } from './language';

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly keycloak = new Keycloak({
    url: environment.keycloakUrl,
    realm: environment.keycloakRealm,
    clientId: environment.keycloakClientId,
  });

  private readonly loggedIn = signal(false);
  private readonly resolved = signal(false);
  private readyPromise: Promise<void> = Promise.resolve();
  private readonly profile = signal<{ name: string; roles: string[] } | null>(null);

  readonly isLoggedIn = this.loggedIn.asReadonly();
  /** False until Keycloak has answered (or timed out), so the header can avoid flicker. */
  readonly isResolved = this.resolved.asReadonly();
  readonly displayName = computed(() => this.profile()?.name ?? '');
  readonly isAdmin = computed(() => this.profile()?.roles.includes('admin') ?? false);

  /**
   * Starts Keycloak in the background so the app can render straight away. The prices
   * page works without an account, so if Keycloak is slow or down we carry on as a
   * guest after AUTH_TIMEOUT_MS. A late answer still updates the signed-in state.
   */
  init(): void {
    const keycloakInit = this.keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        this.setState(authenticated);
        this.keycloak.onAuthRefreshError = () => this.setState(false);
      })
      .catch((error) => {
        console.warn('Login service unavailable, continuing without sign-in', error);
      })
      .finally(() => this.resolved.set(true));

    const timeout = new Promise<void>((resolve) =>
      setTimeout(() => {
        this.resolved.set(true);
        resolve();
      }, AUTH_TIMEOUT_MS),
    );
    this.readyPromise = Promise.race([keycloakInit, timeout]);
  }

  /** Resolves once Keycloak has answered or the timeout has passed. */
  ready(): Promise<void> {
    return this.readyPromise;
  }

  login(redirectPath = location.pathname): Promise<void> {
    // Keycloak shows its login page in the same language as the app.
    return this.keycloak.login({
      redirectUri: location.origin + redirectPath,
      locale: currentLang(),
    });
  }

  register(): Promise<void> {
    return this.keycloak.register({
      redirectUri: location.origin + '/home',
      locale: currentLang(),
    });
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: location.origin });
  }

  /** Returns a token that is valid for at least another 30 seconds. */
  async token(): Promise<string | undefined> {
    await this.readyPromise;
    if (!this.loggedIn()) return undefined;
    try {
      await this.keycloak.updateToken(30);
      return this.keycloak.token;
    } catch {
      this.setState(false);
      return undefined;
    }
  }

  private setState(authenticated: boolean): void {
    this.loggedIn.set(authenticated);
    const parsed = this.keycloak.tokenParsed;
    this.profile.set(
      authenticated && parsed
        ? {
            name: (parsed['given_name'] as string) ?? (parsed['preferred_username'] as string),
            roles: parsed.realm_access?.roles ?? [],
          }
        : null,
    );
  }
}
