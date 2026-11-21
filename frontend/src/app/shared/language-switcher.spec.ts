import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { provideTestI18n, startLanguage } from '../testing/i18n';
import { formatDkk } from '../core/format';
import { LanguageSwitcher } from './language-switcher';

@Component({
  imports: [LanguageSwitcher, TranslatePipe],
  template: `
    <app-language-switcher />
    <h1>{{ 'prices.title' | translate }}</h1>
    <p class="amount">{{ amount() }}</p>
  `,
})
class Host {
  protected amount = () => formatDkk(1234.5);
}

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
  });

  async function render() {
    startLanguage();
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const button = (lang: string) =>
      page.querySelector<HTMLButtonElement>(`button[lang="${lang}"]`)!;
    return { fixture, page, button };
  }

  it('is a labelled group of two buttons with the active one pressed', async () => {
    const { page, button } = await render();

    expect(page.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Sprog');
    expect(button('da').getAttribute('aria-pressed')).toBe('true');
    expect(button('en').getAttribute('aria-pressed')).toBe('false');
    expect(button('en').getAttribute('aria-label')).toBe('EN – English');
    expect(button('en').type).toBe('button');
  });

  it('switches the visible text, number format and its own label', async () => {
    const { fixture, page, button } = await render();
    expect(page.querySelector('h1')?.textContent).toBe('Elpriser');
    expect(page.querySelector('.amount')?.textContent?.replace(/\s/g, ' ')).toBe('1.234,50 kr.');

    button('en').click();
    await fixture.whenStable();

    expect(page.querySelector('h1')?.textContent).toBe('Electricity prices');
    expect(page.querySelector('.amount')?.textContent?.replace(/\s/g, ' ')).toBe('DKK 1,234.50');
    expect(page.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Language');
    expect(button('en').getAttribute('aria-pressed')).toBe('true');
    expect(localStorage.getItem('greenhour.language')).toBe('en');
  });
});
