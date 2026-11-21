import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { routes } from './app.routes';
import { Auth } from './core/auth';
import { authInterceptor } from './core/auth-interceptor';
import { BundledTranslations, Language, TranslatedTitleStrategy } from './core/language';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideTranslateService({
      loader: provideTranslateLoader(BundledTranslations),
      fallbackLang: 'da',
      lang: 'da',
    }),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
    provideAppInitializer(() => {
      inject(Language).init();
      // Not awaited: the page renders at once and sign-in state follows when ready.
      inject(Auth).init();
    }),
  ],
};
