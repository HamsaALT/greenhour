import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import {
  InterpolationParameters,
  TranslateLoader,
  TranslateService,
  TranslationObject,
} from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import da from '../../i18n/da.json';
import en from '../../i18n/en.json';

export type Lang = 'da' | 'en';
export const LANGUAGES: readonly Lang[] = ['da', 'en'];

const STORAGE_KEY = 'greenhour.language';

/**
 * The active language as a signal. Formatting helpers read it, so anything a
 * template or a computed() formats updates by itself when the language changes.
 */
export const currentLang = signal<Lang>('da');

export const intlLocale = (): string => (currentLang() === 'da' ? 'da-DK' : 'en-GB');

/** Both languages are bundled, so the first render is already translated. */
export class BundledTranslations implements TranslateLoader {
  getTranslation(lang: string): Observable<TranslationObject> {
    return of((lang === 'en' ? en : da) as TranslationObject);
  }
}

// Lets plain functions (formatters, error mapping) translate without injection.
let translator: (key: string, params?: InterpolationParameters) => string = (key) => key;

export function t(key: string, params?: InterpolationParameters): string {
  currentLang(); // tracked, so callers inside computed() and templates re-run on a language change
  return translator(key, params);
}

@Injectable({ providedIn: 'root' })
export class Language {
  private readonly translate = inject(TranslateService);

  readonly current = currentLang.asReadonly();

  /** Danish unless the user picked English before. The browser's language is ignored on purpose. */
  init(): void {
    translator = (key, params) => this.translate.instant(key, params) as string;
    this.apply(readStored() ?? 'da');
  }

  set(lang: Lang): void {
    this.apply(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Private mode or blocked storage: the choice just won't survive a reload.
    }
  }

  private apply(lang: Lang): void {
    // Load the translations before flipping the signal, so everything that
    // reacts to the signal already sees the new texts.
    this.translate.use(lang);
    currentLang.set(lang);
    document.documentElement.lang = lang;
  }
}

function readStored(): Lang | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'da' || stored === 'en' ? stored : null;
  } catch {
    return null;
  }
}

/** Route titles are translation keys; this turns them into "Priser · GreenHour". */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly translate = inject(TranslateService);
  private lastSnapshot: RouterStateSnapshot | null = null;

  constructor() {
    super();
    this.translate.onLangChange.subscribe(() => {
      if (this.lastSnapshot) this.updateTitle(this.lastSnapshot);
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.lastSnapshot = snapshot;
    const key = this.buildTitle(snapshot);
    this.title.setTitle(key ? `${this.translate.instant(key)} · GreenHour` : 'GreenHour');
  }
}
