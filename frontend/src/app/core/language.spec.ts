import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { provideTestI18n } from '../testing/i18n';
import { Language, currentLang, t } from './language';

describe('Language', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
  });

  afterEach(() => localStorage.clear());

  it('starts in Danish on a first visit, even if the browser prefers English', () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');

    TestBed.inject(Language).init();

    expect(currentLang()).toBe('da');
    expect(document.documentElement.lang).toBe('da');
    expect(t('app.signIn')).toBe('Log ind');
  });

  it('switches the texts and the html lang attribute', () => {
    const language = TestBed.inject(Language);
    language.init();

    language.set('en');

    expect(currentLang()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    expect(TestBed.inject(TranslateService).instant('app.signIn')).toBe('Sign in');
  });

  it('remembers the choice after a reload', () => {
    TestBed.inject(Language).init();
    TestBed.inject(Language).set('en');
    expect(localStorage.getItem('greenhour.language')).toBe('en');

    // A reload is a fresh app reading the same storage.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    TestBed.inject(Language).init();

    expect(currentLang()).toBe('en');
    expect(t('app.signIn')).toBe('Sign in');
  });

  it('ignores a stored value it does not understand', () => {
    localStorage.setItem('greenhour.language', 'klingon');

    TestBed.inject(Language).init();

    expect(currentLang()).toBe('da');
  });
});
