import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LANGUAGES, Language } from '../core/language';

/**
 * Two real buttons in a labelled group. aria-pressed tells screen readers which
 * language is active. Each button's accessible name starts with its visible text
 * ("DA – Dansk"), so voice control users can say "click DA" (WCAG 2.5.3), and the
 * lang attribute makes "Dansk" sound Danish even when the page is in English.
 */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslatePipe],
  template: `
    <div class="switcher" role="group" [attr.aria-label]="'language.label' | translate">
      @for (lang of languages; track lang) {
        <button
          type="button"
          [attr.lang]="lang"
          [attr.aria-pressed]="language.current() === lang"
          [attr.aria-label]="lang.toUpperCase() + ' – ' + ('language.' + lang | translate)"
          (click)="language.set(lang)"
        >
          {{ lang.toUpperCase() }}
        </button>
      }
    </div>
  `,
  styles: `
    .switcher {
      display: inline-flex;
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
    }
    button {
      min-width: 44px;
      height: 40px;
      padding: 0 8px;
      border: none;
      background: var(--surface);
      color: var(--ink);
      font: inherit;
      font-size: var(--text-sm);
      font-weight: 600;
      cursor: pointer;
    }
    button + button {
      border-left: 1px solid var(--line-strong);
    }
    button:hover {
      background: var(--surface-sunken);
    }
    button[aria-pressed='true'] {
      background: var(--accent);
      color: #fff;
    }
    button:first-child {
      border-radius: 5px 0 0 5px;
    }
    button:last-child {
      border-radius: 0 5px 5px 0;
    }
    @media (max-width: 860px) {
      button {
        height: 44px;
      }
    }
    button:focus-visible {
      position: relative;
      z-index: 1;
      outline: 3px solid var(--accent);
      outline-offset: 2px;
    }
  `,
})
export class LanguageSwitcher {
  protected readonly language = inject(Language);
  protected readonly languages = LANGUAGES;
}
