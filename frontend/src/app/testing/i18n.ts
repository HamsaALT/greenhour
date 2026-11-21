import { EnvironmentProviders, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { BundledTranslations, Lang, Language } from '../core/language';

/** The same translation setup as the app, for TestBed. */
export function provideTestI18n(): (Provider | EnvironmentProviders)[] {
  return [
    provideTranslateService({
      loader: provideTranslateLoader(BundledTranslations),
      fallbackLang: 'da',
      lang: 'da',
    }),
  ];
}

/** Starts the language service like the app does, optionally switching language afterwards. */
export function startLanguage(lang?: Lang): Language {
  const language = TestBed.inject(Language);
  language.init();
  if (lang) language.set(lang);
  return language;
}
