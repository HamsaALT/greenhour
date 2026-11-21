import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { Auth } from './core/auth';
import { Icon } from './shared/icon';
import { LanguageSwitcher } from './shared/language-switcher';

interface NavLink {
  path: string;
  label: string;
  exact: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher, Icon],
  host: {
    '(document:keydown.escape)': 'closeMenus(true)',
    '(document:click)': 'onDocumentClick($event)',
  },
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | translate }}</a>
    <header class="topbar">
      <div class="topbar-inner">
        <a routerLink="/" class="brand" [attr.aria-label]="'app.homeLink' | translate">
          <svg width="26" height="26" viewBox="0 0 22 22" aria-hidden="true">
            <circle cx="11" cy="11" r="9.5" fill="none" stroke="currentColor" stroke-width="1.8" />
            <path d="M11 11 V1.5 A9.5 9.5 0 0 1 20.5 11 Z" fill="currentColor" />
          </svg>
          <span>GreenHour</span>
        </a>

        <nav class="nav-desktop" [attr.aria-label]="'app.nav.label' | translate">
          @for (link of links(); track link.path) {
            <a
              [routerLink]="link.path"
              routerLinkActive="active"
              ariaCurrentWhenActive="page"
              [routerLinkActiveOptions]="{ exact: link.exact }"
              >{{ link.label | translate }}</a
            >
          }
        </nav>

        <div class="right">
          <div class="account-desktop" [class.pending]="!auth.isResolved()">
            @if (auth.isLoggedIn()) {
              <div class="user-menu" #userMenu>
                <button
                  type="button"
                  class="user-button"
                  aria-controls="user-menu-panel"
                  [attr.aria-expanded]="userMenuOpen()"
                  (click)="toggleUserMenu()"
                  #userButton
                >
                  <span class="avatar" aria-hidden="true">{{ initial() }}</span>
                  <span class="user-name">{{ auth.displayName() }}</span>
                  <span class="sr-only">{{ 'app.accountMenu' | translate }}</span>
                  <app-icon name="chevron" />
                </button>
                @if (userMenuOpen()) {
                  <div id="user-menu-panel" class="user-panel">
                    <p class="signed-in">
                      {{ 'app.signedInAs' | translate: { name: auth.displayName() } }}
                    </p>
                    <button type="button" class="panel-action" (click)="auth.logout()">
                      <app-icon name="logout" />
                      {{ 'app.signOut' | translate }}
                    </button>
                  </div>
                }
              </div>
            } @else if (auth.isResolved()) {
              <button type="button" class="btn-link" (click)="auth.register()">
                {{ 'app.createAccount' | translate }}
              </button>
              <button type="button" class="btn btn-small" (click)="auth.login()">
                {{ 'app.signIn' | translate }}
              </button>
            }
          </div>

          <app-language-switcher />

          <button
            type="button"
            class="menu-toggle"
            aria-controls="mobile-menu"
            [attr.aria-expanded]="mobileOpen()"
            (click)="mobileOpen.set(!mobileOpen())"
          >
            <app-icon [name]="mobileOpen() ? 'x' : 'menu'" />
            <span class="sr-only">{{ 'app.menu' | translate }}</span>
          </button>
        </div>
      </div>

      @if (mobileOpen()) {
        <div id="mobile-menu" class="mobile-menu">
          <nav [attr.aria-label]="'app.nav.label' | translate">
            @for (link of links(); track link.path) {
              <a
                [routerLink]="link.path"
                routerLinkActive="active"
                ariaCurrentWhenActive="page"
                [routerLinkActiveOptions]="{ exact: link.exact }"
                >{{ link.label | translate }}</a
              >
            }
          </nav>
          <div class="mobile-account">
            @if (auth.isLoggedIn()) {
              <span class="muted">
                {{ 'app.signedInAs' | translate: { name: auth.displayName() } }}
              </span>
              <button type="button" class="btn btn-secondary" (click)="auth.logout()">
                <app-icon name="logout" />
                {{ 'app.signOut' | translate }}
              </button>
            } @else if (auth.isResolved()) {
              <button type="button" class="btn" (click)="auth.login()">
                {{ 'app.signIn' | translate }}
              </button>
              <button type="button" class="btn btn-secondary" (click)="auth.register()">
                {{ 'app.createAccount' | translate }}
              </button>
            }
          </div>
        </div>
      }
    </header>

    <main id="main" class="page" tabindex="-1">
      <router-outlet />
    </main>

    <footer class="footer">
      {{ 'app.footerBefore' | translate }}
      <a href="https://www.energidataservice.dk" rel="noopener" target="_blank"
        >Energi Data Service</a
      >
      {{ 'app.footerAfter' | translate }}
    </footer>
  `,
  styles: `
    .topbar {
      position: sticky;
      top: 0;
      z-index: 20;
      background: var(--surface);
      border-bottom: 1px solid var(--line);
    }
    .topbar-inner {
      max-width: var(--page-width);
      margin: 0 auto;
      padding: 0 var(--space-5);
      min-height: 64px;
      display: flex;
      align-items: center;
      gap: var(--space-6);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      min-height: var(--touch);
      font-weight: 600;
      font-size: var(--text-lg);
      color: var(--accent);
      text-decoration: none;
      letter-spacing: -0.01em;
    }
    .nav-desktop {
      display: flex;
      gap: var(--space-1);
      flex: 1;
      align-self: stretch;
    }
    nav a {
      display: flex;
      align-items: center;
      padding: 0 var(--space-3);
      color: var(--muted);
      text-decoration: none;
      font-size: var(--text-sm);
      font-weight: 500;
      border-bottom: 3px solid transparent;
      border-top: 3px solid transparent;
    }
    nav a:hover {
      color: var(--ink);
      border-bottom-color: var(--line);
    }
    nav a.active {
      color: var(--accent-ink);
      font-weight: 600;
      border-bottom-color: var(--accent);
    }
    .right {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      margin-left: auto;
    }
    .account-desktop {
      display: flex;
      align-items: center;
      gap: var(--space-3);
    }
    .account-desktop.pending {
      min-width: 120px;
    }
    .user-menu {
      position: relative;
    }
    .user-button {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      min-height: var(--touch);
      padding: 0 var(--space-2) 0 var(--space-1);
      border: 1px solid transparent;
      border-radius: 999px;
      background: none;
      color: var(--ink);
      font: inherit;
      font-size: var(--text-sm);
      font-weight: 500;
      cursor: pointer;
    }
    .user-button:hover,
    .user-button[aria-expanded='true'] {
      background: var(--surface-sunken);
      border-color: var(--line);
    }
    .avatar {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--accent-soft);
      color: var(--accent-ink);
      font-weight: 600;
    }
    .user-panel {
      position: absolute;
      right: 0;
      top: calc(100% + 6px);
      min-width: 220px;
      padding: var(--space-2);
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-md);
    }
    .signed-in {
      margin: 0;
      padding: var(--space-2) var(--space-3);
      font-size: var(--text-sm);
      color: var(--muted);
      border-bottom: 1px solid var(--line);
    }
    .panel-action {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      width: 100%;
      min-height: var(--touch);
      margin-top: var(--space-1);
      padding: 0 var(--space-3);
      border: none;
      border-radius: var(--radius-sm);
      background: none;
      color: var(--ink);
      font: inherit;
      font-size: var(--text-sm);
      text-align: left;
      cursor: pointer;
    }
    .panel-action:hover {
      background: var(--surface-sunken);
    }
    .menu-toggle {
      display: none;
      place-items: center;
      width: var(--touch);
      height: var(--touch);
      padding: 10px;
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      background: var(--surface);
      color: var(--ink);
      cursor: pointer;
    }
    .mobile-menu {
      border-top: 1px solid var(--line);
      padding: var(--space-2) var(--space-4) var(--space-4);
    }
    .mobile-menu nav {
      display: flex;
      flex-direction: column;
    }
    .mobile-menu nav a {
      min-height: var(--touch);
      padding: 0 var(--space-3);
      font-size: var(--text-md);
      border: none;
      border-left: 4px solid transparent;
      border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    }
    .mobile-menu nav a.active {
      border-left-color: var(--accent);
      background: var(--accent-soft);
    }
    .mobile-account {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      margin-top: var(--space-3);
      padding-top: var(--space-3);
      border-top: 1px solid var(--line);
    }
    .mobile-account .btn {
      min-height: var(--touch);
    }
    .footer {
      max-width: var(--page-width);
      margin: var(--space-10) auto var(--space-8);
      padding: var(--space-5) var(--space-5) 0;
      border-top: 1px solid var(--line);
      font-size: var(--text-sm);
      color: var(--muted);
    }
    main:focus {
      outline: none;
    }
    @media (max-width: 860px) {
      .topbar-inner {
        padding: var(--space-2) var(--space-4);
        gap: var(--space-3);
      }
      .nav-desktop,
      .account-desktop {
        display: none;
      }
      .menu-toggle {
        display: grid;
      }
    }
  `,
})
export class App {
  protected readonly auth = inject(Auth);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly userMenu = viewChild<ElementRef<HTMLElement>>('userMenu');
  private readonly userButton = viewChild<ElementRef<HTMLButtonElement>>('userButton');

  protected readonly mobileOpen = signal(false);
  protected readonly userMenuOpen = signal(false);

  protected readonly initial = computed(() =>
    this.auth.displayName().charAt(0).toLocaleUpperCase(),
  );

  protected readonly links = computed<NavLink[]>(() => {
    const links: NavLink[] = [{ path: '/', label: 'app.nav.prices', exact: true }];
    if (this.auth.isLoggedIn()) {
      links.push(
        { path: '/plan', label: 'app.nav.plan', exact: false },
        { path: '/history', label: 'app.nav.history', exact: false },
        { path: '/home', label: 'app.nav.home', exact: false },
      );
      if (this.auth.isAdmin()) links.push({ path: '/admin', label: 'app.nav.admin', exact: false });
    }
    return links;
  });

  constructor() {
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.closeMenus(false));
  }

  protected toggleUserMenu(): void {
    this.userMenuOpen.update((open) => !open);
  }

  protected closeMenus(returnFocus: boolean): void {
    if (returnFocus && this.userMenuOpen()) this.userButton()?.nativeElement.focus();
    this.userMenuOpen.set(false);
    this.mobileOpen.set(false);
  }

  protected onDocumentClick(event: MouseEvent): void {
    const target = event.target as Node;
    if (this.userMenuOpen() && !this.userMenu()?.nativeElement.contains(target)) {
      this.userMenuOpen.set(false);
    }
    if (this.mobileOpen() && !this.host.nativeElement.querySelector('.topbar')?.contains(target)) {
      this.mobileOpen.set(false);
    }
  }
}
